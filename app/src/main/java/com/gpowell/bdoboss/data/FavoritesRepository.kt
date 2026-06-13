package com.gpowell.bdoboss.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class FavoriteType { PAGE, ITEM, PLAYER }

@Serializable
data class Favorite(
    val id: Long,
    val type: FavoriteType,
    val title: String,
    val subtitle: String = "",
    val addedAt: Long = 0L,       // epoch seconds; repo stamps on add
    val url: String = "",         // PAGE (also ITEM codex url)
    val itemId: Int = 0,          // ITEM
    val region: String = "",      // PLAYER
    val familyName: String = "",  // PLAYER
    val targetPrice: Long = 0L,   // ITEM — optional buy-target silver (0 = none)
) {
    companion object {
        private val serializer = ListSerializer(serializer())

        // -----------------------------------------------------------------
        // Codec: encode/decode List<Favorite> <-> JSON string
        // Garbage / empty input decodes to emptyList (same pattern as QuietRules)
        // -----------------------------------------------------------------

        fun encode(list: List<Favorite>): String =
            Json.encodeToString(serializer, list)

        fun decode(raw: String): List<Favorite> =
            runCatching { Json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

        // -----------------------------------------------------------------
        // Natural-key matcher — pure, unit-testable function.
        //
        // Rules per type:
        //   PAGE   → same type + url (non-empty)
        //   ITEM   → same type + itemId (when nonzero); falls back to url when itemId==0
        //   PLAYER → same type + region + familyName (case-insensitive)
        //
        // Returns the first matching Favorite in [list], or null if none found.
        // Cross-type never matches (type must be identical).
        // -----------------------------------------------------------------

        fun findMatch(
            list: List<Favorite>,
            type: FavoriteType,
            url: String = "",
            itemId: Int = 0,
            region: String = "",
            familyName: String = "",
        ): Favorite? = list.firstOrNull { existing ->
            if (existing.type != type) return@firstOrNull false
            when (type) {
                FavoriteType.PAGE -> url.isNotEmpty() && existing.url == url

                FavoriteType.ITEM -> if (itemId != 0) {
                    existing.itemId == itemId
                } else {
                    // itemId==0 on both sides: fall back to url
                    url.isNotEmpty() && existing.itemId == 0 && existing.url == url
                }

                FavoriteType.PLAYER ->
                    existing.region.equals(region, ignoreCase = true) &&
                        existing.familyName.equals(familyName, ignoreCase = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private DataStore — separate from "settings" so reads/writes are independent
// ---------------------------------------------------------------------------

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

class FavoritesRepository(private val context: Context) {

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites_json")
    }

    // -----------------------------------------------------------------------
    // Sorted newest-first: descending addedAt, then descending id as tiebreak
    // -----------------------------------------------------------------------

    val favorites: Flow<List<Favorite>> = context.favoritesDataStore.data.map { prefs ->
        Favorite.decode(prefs[Keys.FAVORITES] ?: "")
            .sortedWith(compareByDescending<Favorite> { it.addedAt }.thenByDescending { it.id })
    }

    // -----------------------------------------------------------------------
    // add — stamps id (max existing id + 1) and addedAt (epoch seconds).
    // No-op duplicate: returns the existing Favorite if a natural-key match found.
    // -----------------------------------------------------------------------

    suspend fun add(
        type: FavoriteType,
        title: String,
        subtitle: String = "",
        url: String = "",
        itemId: Int = 0,
        region: String = "",
        familyName: String = "",
    ): Favorite {
        var result: Favorite? = null
        context.favoritesDataStore.edit { prefs ->
            val current = Favorite.decode(prefs[Keys.FAVORITES] ?: "")

            val existing = Favorite.findMatch(
                list = current,
                type = type,
                url = url,
                itemId = itemId,
                region = region,
                familyName = familyName,
            )
            if (existing != null) {
                result = existing
                return@edit
            }

            val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
            val newFav = Favorite(
                id = newId,
                type = type,
                title = title,
                subtitle = subtitle,
                addedAt = System.currentTimeMillis() / 1000L,
                url = url,
                itemId = itemId,
                region = region,
                familyName = familyName,
            )
            prefs[Keys.FAVORITES] = Favorite.encode(current + newFav)
            result = newFav
        }
        return result!!
    }

    // -----------------------------------------------------------------------
    // remove — removes by id; no-op if id not found
    // -----------------------------------------------------------------------

    suspend fun remove(id: Long) {
        context.favoritesDataStore.edit { prefs ->
            val current = Favorite.decode(prefs[Keys.FAVORITES] ?: "")
            prefs[Keys.FAVORITES] = Favorite.encode(current.filter { it.id != id })
        }
    }

    // -----------------------------------------------------------------------
    // toggle — adds if absent (returns true), removes if present (returns false)
    // -----------------------------------------------------------------------

    suspend fun toggle(
        type: FavoriteType,
        title: String,
        subtitle: String = "",
        url: String = "",
        itemId: Int = 0,
        region: String = "",
        familyName: String = "",
    ): Boolean {
        var added = false
        context.favoritesDataStore.edit { prefs ->
            val current = Favorite.decode(prefs[Keys.FAVORITES] ?: "")

            val existing = Favorite.findMatch(
                list = current,
                type = type,
                url = url,
                itemId = itemId,
                region = region,
                familyName = familyName,
            )
            if (existing != null) {
                // Present → remove
                prefs[Keys.FAVORITES] = Favorite.encode(current.filter { it.id != existing.id })
                added = false
            } else {
                // Absent → add
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                val newFav = Favorite(
                    id = newId,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    addedAt = System.currentTimeMillis() / 1000L,
                    url = url,
                    itemId = itemId,
                    region = region,
                    familyName = familyName,
                )
                prefs[Keys.FAVORITES] = Favorite.encode(current + newFav)
                added = true
            }
        }
        return added
    }

    // -----------------------------------------------------------------------
    // setTarget — set/clear an ITEM favorite's buy-target price (0 clears).
    // If the item isn't yet favorited, adds it to the watchlist with the target.
    // Title is best-effort (caller passes the known name); no-op if itemId == 0.
    // -----------------------------------------------------------------------

    suspend fun setTarget(itemId: Int, target: Long, title: String = "", url: String = "") {
        if (itemId == 0) return
        context.favoritesDataStore.edit { prefs ->
            val current = Favorite.decode(prefs[Keys.FAVORITES] ?: "")
            val existing = Favorite.findMatch(current, FavoriteType.ITEM, itemId = itemId)
            val updated = if (existing != null) {
                current.map { if (it.id == existing.id) it.copy(targetPrice = target) else it }
            } else {
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                current + Favorite(
                    id = newId,
                    type = FavoriteType.ITEM,
                    title = title.ifBlank { "Item $itemId" },
                    addedAt = System.currentTimeMillis() / 1000L,
                    url = url,
                    itemId = itemId,
                    targetPrice = target,
                )
            }
            prefs[Keys.FAVORITES] = Favorite.encode(updated)
        }
    }

    // -----------------------------------------------------------------------
    // isFavorite — convenience helper; reads current snapshot once
    // -----------------------------------------------------------------------

    suspend fun isFavorite(
        type: FavoriteType,
        url: String = "",
        itemId: Int = 0,
        region: String = "",
        familyName: String = "",
    ): Boolean {
        val current = favorites.first()
        return Favorite.findMatch(
            list = current,
            type = type,
            url = url,
            itemId = itemId,
            region = region,
            familyName = familyName,
        ) != null
    }
}
