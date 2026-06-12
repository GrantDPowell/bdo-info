package com.gpowell.bdoboss.data.market

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One entry of the bundled item index (assets/item_index.json) — generated from
 * arsha.io's keyless `GET /util/db?lang=en` dump ({id, name, grade} rows for the
 * whole item DB), trimmed of timed "(N Days)" rentals and "[Event]" junk
 * (~62k entries, ~3.7MB minified). arsha has NO name search, so all Market-tab
 * name lookups run against this local index; live prices then come from ids.
 *
 * Grades follow BDO rarity: 0 white, 1 green, 2 blue, 3 gold, 4 orange/red,
 * 5 "Sovereign" red.
 */
@Serializable
data class IndexedItem(
    val id: Int,
    val name: String,
    val grade: Int = 0,
)

/**
 * Pure ranking function (unit-tested without Android). Case-insensitive
 * substring match over [items] with tiered ranking:
 *
 *  1. name starts with the query
 *  2. any word of the name starts with the query
 *  3. name contains the query anywhere
 *
 * Shorter names win ties within a tier (then name, then id for stability).
 * Rows with identical (name, grade) collapse to the lowest id — the dump holds
 * thousands of quest/variant duplicates and the lowest id is the canonical one.
 */
fun searchIndex(items: List<IndexedItem>, query: String, limit: Int = 30): List<IndexedItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()

    data class Ranked(val item: IndexedItem, val tier: Int)

    val ranked = items.mapNotNull { item ->
        val name = item.name.lowercase()
        val tier = when {
            name.startsWith(q) -> 0
            name.split(WORD_SPLIT).any { it.startsWith(q) } -> 1
            name.contains(q) -> 2
            else -> return@mapNotNull null
        }
        Ranked(item, tier)
    }.sortedWith(
        compareBy({ it.tier }, { it.item.name.length }, { it.item.name }, { it.item.id }),
    )

    val seen = HashSet<Pair<String, Int>>()
    val out = ArrayList<IndexedItem>(limit)
    for (r in ranked) {
        if (out.size >= limit) break
        if (seen.add(r.item.name.lowercase() to r.item.grade)) out.add(r.item)
    }
    return out
}

private val WORD_SPLIT = Regex("[^a-z0-9]+")

/**
 * Loads the bundled index lazily off the main thread and answers name searches
 * and id lookups. Construct once near the activity root and share — the parsed
 * list (~62k entries) lives for the instance's lifetime.
 */
class ItemIndexRepository(private val context: Context) {

    @Volatile
    private var loaded: Loaded? = null

    private class Loaded(val items: List<IndexedItem>) {
        val byId: Map<Int, IndexedItem> = items.associateBy { it.id }
    }

    /** First call parses the ~3.7MB asset — never call on the main thread. */
    @WorkerThread
    fun search(query: String, limit: Int = 30): List<IndexedItem> =
        searchIndex(load().items, query, limit)

    /** First call parses the asset — never call on the main thread. */
    @WorkerThread
    fun byId(id: Int): IndexedItem? = load().byId[id]

    private fun load(): Loaded = loaded ?: synchronized(this) {
        loaded ?: Loaded(parseAsset()).also { loaded = it }
    }

    /** Garbage/missing asset degrades to an empty index (search returns nothing). */
    private fun parseAsset(): List<IndexedItem> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        INDEX_JSON.decodeFromString(ListSerializer(IndexedItem.serializer()), text)
    }.getOrDefault(emptyList())

    companion object {
        private const val ASSET = "item_index.json"
        private val INDEX_JSON = Json { ignoreUnknownKeys = true }
    }
}
