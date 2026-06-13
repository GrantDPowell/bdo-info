package com.gpowell.bdoboss.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the *real* Central Market item icon (a bdocodex `.webp`) for an item.
 *
 * There is no id→icon formula: bdocodex shares icons across items, so the icon
 * filename is NOT the item id (e.g. Black Stone id 16001 → `00000008.webp`) and
 * the path carries a per-category subfolder. The only generic source is
 * bdocodex's autocomplete (`ac.php?l=us&term=<name>`), which returns each match's
 * full `icon` path. We query by name, then map every returned id→url (so one
 * lookup warms several items) and cache forever in-process. Misses cache as null
 * so we don't re-query; the UI falls back to a grade monogram.
 */
object ItemIconResolver {

    private const val AC_URL = "https://bdocodex.com/ac.php"

    private val client = OkHttpClient()
    // id -> url, or id present with null meaning "resolved, no icon".
    private val cache = ConcurrentHashMap<Int, String?>()

    /** Cached url if already resolved (including null for a known miss). */
    fun cached(itemId: Int): String? = cache[itemId]

    fun isResolved(itemId: Int): Boolean = cache.containsKey(itemId)

    /**
     * Resolve [itemId]'s icon url, querying bdocodex autocomplete by [name] if
     * needed. Returns null on miss/error (caller shows the monogram). Safe to
     * call repeatedly — results are cached.
     */
    suspend fun resolve(itemId: Int, name: String): String? {
        if (cache.containsKey(itemId)) return cache[itemId]
        if (name.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val resolved = runCatching { fetchIcons(name) }.getOrDefault(emptyMap())
            // Cache every id the lookup returned (warms siblings), plus a null
            // sentinel for the requested id if it wasn't among them.
            resolved.forEach { (id, url) -> cache[id] = url }
            if (!cache.containsKey(itemId)) cache[itemId] = null
            cache[itemId]
        }
    }

    private fun fetchIcons(term: String): Map<Int, String> {
        val url = "$AC_URL?l=us&term=${term.encodeUrl()}"
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string() ?: return emptyMap()
            return parseAcIcons(body)
        }
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

private val AC_JSON = Json { ignoreUnknownKeys = true }

/** Build the absolute icon url. [iconPath] is e.g. "items", [icon] the rel path. */
internal fun buildIconUrl(iconPath: String, icon: String): String =
    "https://bdocodex.com/$iconPath/$icon"

/**
 * Parse a bdocodex `ac.php` autocomplete body (a BOM-prefixed JSON array) into
 * id→icon-url for every Item entry that has an icon. Garbage parses to empty.
 */
internal fun parseAcIcons(body: String): Map<Int, String> = runCatching {
    // Strip any leading BOM / whitespace before the JSON array.
    val clean = body.dropWhile { it == '﻿' || it.isWhitespace() }
    AC_JSON.parseToJsonElement(clean).jsonArray.mapNotNull { el ->
        val o = el.jsonObject
        val id = o["value"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val linkType = o["link_type"]?.jsonPrimitive?.contentOrNull
        if (linkType != null && linkType != "item") return@mapNotNull null
        val icon = o["icon"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val iconPath = o["icon_path"]?.jsonPrimitive?.contentOrNull ?: "items"
        id to buildIconUrl(iconPath, icon)
    }.toMap()
}.getOrDefault(emptyMap())
