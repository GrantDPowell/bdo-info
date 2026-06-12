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
 * Keyless [MarketSource] backed by https://api.arsha.io (community proxy for
 * Pearl Abyss's marketplace API). NO API key involved — never returns
 * [ApiResult.NoKey].
 *
 * Endpoint reality, probed live 2026-06-12:
 *  - `GET /v2/{region}/item?id={csv}&lang=en` — batched; single id returns one
 *    JSON object, multiple ids return an array. Fields: name, id, sid,
 *    basePrice, currentStock, totalTrades, priceMin, priceMax, lastSoldPrice,
 *    lastSoldTime (epoch sec). Items not currently marketable return all-zero rows.
 *  - `GET /v2/{region}/GetWorldMarketSubList?id={id}&lang=en` — every
 *    enhancement level (sid) of one item, same row shape.
 *  - `GET /v2/{region}/search?ids={csv}&lang=en` — ID-BASED ONLY; there is no
 *    keyword/name parameter (probed: `?keyword=` → 400 "Missing required
 *    parameter 'ids'"). Slim rows: name, id, currentStock, basePrice, totalTrades.
 *  - `GET /v2/{region}/GetWorldMarketWaitList?lang=en` — array of
 *    {name, id, sid, price, liveAt (epoch sec)}.
 *  - `GET /v2/{region}/GetMarketPriceInfo?id={id}&sid={enh}&lang=en` —
 *    {name, id, sid, history: {"<epochMillis>": price, ...}} (~90 daily points).
 *
 * Upstream hiccups: arsha occasionally returns 500 code 103 ("blocked by
 * Imperva") when Pearl Abyss rate-limits it; that surfaces here as
 * [ApiResult.HttpError] 500 and is transient.
 *
 * Caching: in-memory, per-instance. 30-min TTL for prices/itemAll/search,
 * 5-min for waitList. Only successful results are cached.
 */
class ArshaSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.arsha.io",
    private val region: String = DEFAULT_REGION,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : MarketSource {

    companion object {
        const val DEFAULT_REGION = "na"
        private const val PRICE_TTL_MS = 30L * 60 * 1000
        private const val WAITLIST_TTL_MS = 5L * 60 * 1000
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Typed caches, all guarded by [lock]. Values are immutable lists.
    private val lock = Any()
    private val priceCache = mutableMapOf<String, Pair<Long, List<MarketPrice>>>()
    private val historyCache = mutableMapOf<String, Pair<Long, List<PricePoint>>>()
    private var waitListCache: Pair<Long, List<WaitListEntry>>? = null

    // =========================================================================
    // MarketSource
    // =========================================================================

    override suspend fun prices(itemIds: List<Int>): ApiResult<List<MarketPrice>> {
        if (itemIds.isEmpty()) return ApiResult.Success(emptyList())
        val csv = itemIds.distinct().sorted().joinToString(",")
        return cachedPrices("prices:$csv", PRICE_TTL_MS) {
            fetchParsed("/v2/$region/item", mapOf("id" to csv), ::parseMarketPrices)
        }
    }

    override suspend fun itemAll(itemId: Int): ApiResult<List<MarketPrice>> =
        cachedPrices("itemAll:$itemId", PRICE_TTL_MS) {
            fetchParsed(
                "/v2/$region/GetWorldMarketSubList",
                mapOf("id" to itemId.toString()),
                ::parseMarketPrices,
            )
        }

    /**
     * arsha has NO name search — /v2/{region}/search only accepts `ids`.
     * Numeric queries ("44195" or "44195,16001") are routed there; anything else
     * returns [ApiResult.HttpError] 404. The Market tab's name search must come
     * from a local id/name index (arsha's /util/db dump or our bundled data) or
     * from the keyed BDO Alerts source once available.
     */
    override suspend fun search(query: String): ApiResult<List<MarketPrice>> {
        val ids = query.split(',').map { it.trim() }
        if (ids.isEmpty() || ids.any { it.toIntOrNull() == null }) {
            return ApiResult.HttpError(404)
        }
        val csv = ids.joinToString(",")
        return cachedPrices("search:$csv", PRICE_TTL_MS) {
            fetchParsed("/v2/$region/search", mapOf("ids" to csv), ::parseSearchPrices)
        }
    }

    override suspend fun waitList(): ApiResult<List<WaitListEntry>> {
        synchronized(lock) {
            waitListCache?.let { (at, value) ->
                if (clock() - at < WAITLIST_TTL_MS) return ApiResult.Success(value)
            }
        }
        val result = fetchParsed("/v2/$region/GetWorldMarketWaitList", emptyMap(), ::parseWaitList)
        if (result is ApiResult.Success) {
            synchronized(lock) { waitListCache = clock() to result.data }
        }
        return result
    }

    override suspend fun history(itemId: Int, enhancement: Int): ApiResult<List<PricePoint>> {
        val key = "history:$itemId:$enhancement"
        synchronized(lock) {
            historyCache[key]?.let { (at, value) ->
                if (clock() - at < PRICE_TTL_MS) return ApiResult.Success(value)
            }
        }
        val result = fetchParsed(
            "/v2/$region/GetMarketPriceInfo",
            mapOf("id" to itemId.toString(), "sid" to enhancement.toString()),
            ::parseHistory,
        )
        if (result is ApiResult.Success) {
            synchronized(lock) { historyCache[key] = clock() to result.data }
        }
        return result
    }

    // =========================================================================
    // Transport + cache plumbing
    // =========================================================================

    private suspend fun cachedPrices(
        key: String,
        ttlMs: Long,
        fetch: suspend () -> ApiResult<List<MarketPrice>>,
    ): ApiResult<List<MarketPrice>> {
        synchronized(lock) {
            priceCache[key]?.let { (at, value) ->
                if (clock() - at < ttlMs) return ApiResult.Success(value)
            }
        }
        val result = fetch()
        if (result is ApiResult.Success) {
            synchronized(lock) { priceCache[key] = clock() to result.data }
        }
        return result
    }

    /**
     * GET [path]?[params]&lang=en, parse body with [parse].
     * IOException → Offline; non-2xx → HttpError(code); body/parse failure →
     * HttpError(-1) (same decode sentinel as BdoAlertsApi).
     */
    private suspend fun <T> fetchParsed(
        path: String,
        params: Map<String, String>,
        parse: (JsonElement) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val query = (params + ("lang" to "en")).entries.joinToString("&") { (k, v) ->
            "${k.encodeUrl()}=${v.encodeUrl()}"
        }
        val request = Request.Builder().url("$baseUrl$path?$query").build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext ApiResult.HttpError(response.code)
            val body = response.body?.string() ?: return@withContext ApiResult.HttpError(-1)
            runCatching { ApiResult.Success(parse(json.parseToJsonElement(body))) }
                .getOrElse { ApiResult.HttpError(-1) }
        } catch (_: IOException) {
            ApiResult.Offline
        }
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

// =============================================================================
// Pure parsing (internal for unit tests against real captured fixtures)
// =============================================================================

/** arsha returns a bare object for one result and an array for many — normalize. */
private fun JsonElement.asObjects(): List<JsonObject> = when (this) {
    is JsonArray -> map { it.jsonObject }
    is JsonObject -> listOf(this)
    else -> emptyList()
}

private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0
private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

/** Parses /item and /GetWorldMarketSubList rows (full marketplace row shape). */
internal fun parseMarketPrices(el: JsonElement): List<MarketPrice> = el.asObjects().map { o ->
    MarketPrice(
        itemId = o.int("id"),
        enhancement = o.int("sid"),
        name = o.str("name"),
        basePrice = o.long("basePrice"),
        stock = o.long("currentStock"),
        lastSoldPrice = o.long("lastSoldPrice"),
        lastSoldAt = o.long("lastSoldTime"),
    )
}

/** Parses /search rows (slim shape: no sid / lastSold fields → enhancement 0). */
internal fun parseSearchPrices(el: JsonElement): List<MarketPrice> = el.asObjects().map { o ->
    MarketPrice(
        itemId = o.int("id"),
        enhancement = 0,
        name = o.str("name"),
        basePrice = o.long("basePrice"),
        stock = o.long("currentStock"),
    )
}

/** Parses /GetWorldMarketWaitList rows. */
internal fun parseWaitList(el: JsonElement): List<WaitListEntry> = el.asObjects().map { o ->
    WaitListEntry(
        itemId = o.int("id"),
        enhancement = o.int("sid"),
        name = o.str("name"),
        price = o.long("price"),
        liveAt = o.long("liveAt"),
    )
}

/**
 * Parses /GetMarketPriceInfo: `history` is a map of epoch-MILLIS string keys to
 * prices; emitted as epoch-second [PricePoint]s sorted ascending by time.
 */
internal fun parseHistory(el: JsonElement): List<PricePoint> {
    val history = el.jsonObject["history"]?.jsonObject ?: return emptyList()
    return history.entries
        .mapNotNull { (k, v) ->
            val millis = k.toLongOrNull() ?: return@mapNotNull null
            val price = v.jsonPrimitive.longOrNull ?: return@mapNotNull null
            PricePoint(at = millis / 1000, price = price)
        }
        .sortedBy { it.at }
}
