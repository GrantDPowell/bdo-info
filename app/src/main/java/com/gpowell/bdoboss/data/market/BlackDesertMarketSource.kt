package com.gpowell.bdoboss.data.market

import com.gpowell.bdoboss.data.api.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Secondary keyless [MarketSource] backed by https://api.blackdesertmarket.com
 * (sobekcore's Central Market mirror). Used as a fallback when [ArshaSource] is
 * down — arsha's upstream Imperva blocks can last minutes, and this mirror has
 * stayed up through them.
 *
 * Endpoint reality, probed live 2026-06-13 (every response is an envelope
 * `{"code":"SUCCESS","data":...}`; non-SUCCESS ⇒ error):
 *  - `GET /item/{id}?region=na&language=en` — ARRAY of every enhancement level:
 *    {id, name, count (stock), grade, basePrice, enhancement, tradeCount,
 *    mainCategory, subCategory}. (No batch: one id per call. No lastSold/min/max.)
 *  - `GET /list/{main}/{sub}?region=na&language=en` — category rows:
 *    {id, name, count, grade, basePrice} (no tradeCount at list level).
 *
 * Not provided here (fall through to arsha / degrade): name search, wait list,
 * price history. Those return [ApiResult.HttpError] 404.
 */
class BlackDesertMarketSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.blackdesertmarket.com",
    private val region: String = "na",
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MarketSource {

    companion object {
        private const val PRICE_TTL_MS = 30L * 60 * 1000
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val itemCache = mutableMapOf<Int, Pair<Long, List<MarketPrice>>>()
    private val listingCache = mutableMapOf<String, Pair<Long, List<MarketListing>>>()

    override suspend fun prices(itemIds: List<Int>): ApiResult<List<MarketPrice>> {
        if (itemIds.isEmpty()) return ApiResult.Success(emptyList())
        val out = ArrayList<MarketPrice>(itemIds.size)
        var lastError: ApiResult<List<MarketPrice>>? = null
        for (id in itemIds.distinct()) {
            when (val r = itemAll(id)) {
                is ApiResult.Success ->
                    (r.data.firstOrNull { it.enhancement == 0 } ?: r.data.firstOrNull())
                        ?.let { out.add(it) }
                else -> lastError = r
            }
        }
        // Surface an error only if we got nothing at all.
        return if (out.isEmpty() && lastError != null) lastError else ApiResult.Success(out)
    }

    override suspend fun itemAll(itemId: Int): ApiResult<List<MarketPrice>> {
        synchronized(lock) {
            itemCache[itemId]?.let { (at, value) ->
                if (clock() - at < PRICE_TTL_MS) return ApiResult.Success(value)
            }
        }
        val result = fetchData("/item/$itemId").map { parseBdmItems(it) }
        if (result is ApiResult.Success) {
            synchronized(lock) { itemCache[itemId] = clock() to result.data }
        }
        return result
    }

    override suspend fun categoryList(
        mainCategory: Int,
        subCategory: Int,
    ): ApiResult<List<MarketListing>> {
        val key = "$mainCategory:$subCategory"
        synchronized(lock) {
            listingCache[key]?.let { (at, value) ->
                if (clock() - at < PRICE_TTL_MS) return ApiResult.Success(value)
            }
        }
        val result = fetchData("/list/$mainCategory/$subCategory")
            .map { parseBdmListings(it, mainCategory, subCategory) }
        if (result is ApiResult.Success) {
            synchronized(lock) { listingCache[key] = clock() to result.data }
        }
        return result
    }

    // Unsupported by this mirror — callers fall back to arsha or degrade.
    override suspend fun search(query: String): ApiResult<List<MarketPrice>> = ApiResult.HttpError(404)
    override suspend fun waitList(): ApiResult<List<WaitListEntry>> = ApiResult.HttpError(404)
    override suspend fun history(itemId: Int, enhancement: Int): ApiResult<List<PricePoint>> =
        ApiResult.HttpError(404)

    /**
     * GET [path]?region&language, unwrap the `{code,data}` envelope. Returns the
     * `data` element on SUCCESS; IOException → Offline; bad envelope → HttpError(-1).
     */
    private suspend fun fetchData(path: String): ApiResult<JsonElement> = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path?region=$region&language=en"
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext ApiResult.HttpError(response.code)
                val body = response.body?.string() ?: return@withContext ApiResult.HttpError(-1)
                runCatching {
                    val root = json.parseToJsonElement(body).jsonObject
                    val code = root["code"]?.jsonPrimitive?.contentOrNull
                    val data = root["data"]
                    if (code == "SUCCESS" && data != null) ApiResult.Success(data)
                    else ApiResult.HttpError(-1)
                }.getOrElse { ApiResult.HttpError(-1) }
            }
        } catch (_: IOException) {
            ApiResult.Offline
        }
    }
}

// =============================================================================
// Pure parsing (internal for unit tests against captured fixtures)
// =============================================================================

private fun JsonElement.bdmObjects(): List<JsonObject> = when (this) {
    is JsonArray -> map { it.jsonObject }
    is JsonObject -> listOf(this)
    else -> emptyList()
}

private fun JsonObject.l(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
private fun JsonObject.i(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.s(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

/** Parses blackdesertmarket `/item/{id}` rows (one per enhancement level). */
internal fun parseBdmItems(data: JsonElement): List<MarketPrice> = data.bdmObjects().map { o ->
    MarketPrice(
        itemId = o.i("id"),
        enhancement = o.i("enhancement"),
        name = o.s("name"),
        basePrice = o.l("basePrice"),
        stock = o.l("count"),
        totalTrades = o.l("tradeCount"),
    )
}

/** Parses blackdesertmarket `/list/{main}/{sub}` rows into [MarketListing]. */
internal fun parseBdmListings(
    data: JsonElement,
    mainCategory: Int,
    subCategory: Int,
): List<MarketListing> = data.bdmObjects().map { o ->
    MarketListing(
        itemId = o.i("id"),
        name = o.s("name"),
        stock = o.l("count"),
        totalTrades = o.l("tradeCount"), // absent at list level → 0
        basePrice = o.l("basePrice"),
        mainCategory = mainCategory,
        subCategory = subCategory,
    )
}
