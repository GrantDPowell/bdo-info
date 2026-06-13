package com.gpowell.bdoboss.data.market

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Central Market category tree (bundled `assets/market_categories.json`,
 * sourced from andreivreja/veliainn-market-resources — codes verified against
 * live arsha 2026-06-13). arsha serves no category-name metadata, so this static
 * mapping turns `GetWorldMarketList`'s numeric main/sub codes into the names the
 * in-game market shows.
 */
data class MarketSubCategory(val code: Int, val name: String)

data class MarketCategory(
    val code: Int,
    val name: String,
    val subs: List<MarketSubCategory>,
)

/**
 * Pure parser (unit-tested without Android). The asset shape is
 * `{"1":{"name":"Main Weapon","sub_categories":{"1":{"name":"Longsword"},…}},…}`.
 * Mains and subs are returned sorted ascending by numeric code. Garbage input
 * yields an empty list.
 */
fun parseCategories(jsonText: String): List<MarketCategory> = runCatching {
    val root = Json.parseToJsonElement(jsonText).jsonObject
    root.entries.mapNotNull { (mainKey, mainEl) ->
        val mainCode = mainKey.toIntOrNull() ?: return@mapNotNull null
        val mainObj = mainEl.jsonObject
        val mainName = mainObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val subs = mainObj["sub_categories"]?.jsonObject?.entries.orEmpty()
            .mapNotNull { (subKey, subEl) ->
                val subCode = subKey.toIntOrNull() ?: return@mapNotNull null
                val subName = subEl.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                MarketSubCategory(subCode, subName)
            }
            .sortedBy { it.code }
        MarketCategory(mainCode, mainName, subs)
    }.sortedBy { it.code }
}.getOrDefault(emptyList())

/**
 * Loads the bundled category tree lazily off the main thread. Construct once near
 * the activity root and share — the parsed tree (17 mains) lives for the
 * instance's lifetime.
 */
class MarketCategoryRepository(private val context: Context) {

    @Volatile
    private var cached: List<MarketCategory>? = null

    /** First call parses the bundled asset — never call on the main thread. */
    @WorkerThread
    fun categories(): List<MarketCategory> = cached ?: synchronized(this) {
        cached ?: load().also { cached = it }
    }

    /** Display name for a (main, sub) pair, falling back to the code. */
    @WorkerThread
    fun subName(mainCategory: Int, subCategory: Int): String {
        val main = categories().firstOrNull { it.code == mainCategory }
        return main?.subs?.firstOrNull { it.code == subCategory }?.name ?: "Category $subCategory"
    }

    private fun load(): List<MarketCategory> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        parseCategories(text)
    }.getOrDefault(emptyList())

    companion object {
        private const val ASSET = "market_categories.json"
    }
}
