package com.gpowell.bdoboss.data.market

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
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
    // id -> url. A known miss is stored as "" (ConcurrentHashMap forbids null values).
    private val cache = ConcurrentHashMap<Int, String>()
    private const val MISS = ""

    // bdocodex is Imperva-fronted and rate-limits bursts. A scrolling list would
    // otherwise fire dozens of ac.php lookups at once → all throttled → no icons.
    // Cap concurrency and dedupe in-flight requests per name so we stay polite.
    private val gate = Semaphore(3)
    private val inflightLock = Mutex()
    private val inflight = HashMap<String, kotlinx.coroutines.Deferred<Map<Int, String>>>()

    // Disk-persisted id→url map so resolved icons survive app restarts (and we never
    // re-hit bdocodex for an item we've seen). Wired up via [attach] from the Application.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistMutex = Mutex()
    private val persistJson = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(Int.serializer(), String.serializer())
    @Volatile private var cacheFile: File? = null
    @Volatile private var dirty = false

    /** Load the persisted icon-URL cache and start a periodic flush. Call once at app start. */
    fun attach(context: Context) {
        if (cacheFile != null) return
        val f = File(context.filesDir, "icon_url_cache.json")
        cacheFile = f
        ioScope.launch {
            runCatching {
                if (f.exists()) {
                    persistJson.decodeFromString(mapSerializer, f.readText())
                        .forEach { (id, url) -> cache.putIfAbsent(id, url) }
                }
            }
            // Debounced background flush whenever new entries were resolved.
            while (true) {
                delay(5_000)
                if (dirty) {
                    dirty = false
                    persistMutex.withLock {
                        // Persist only real hits — never the "" miss sentinels (those are
                        // transient and should be re-resolvable after a bdocodex recovery).
                        val hits = cache.filterValues { it.isNotEmpty() }
                        runCatching { f.writeText(persistJson.encodeToString(mapSerializer, hits)) }
                    }
                }
            }
        }
    }

    /** Cached url if already resolved (null = not resolved or a known miss). */
    fun cached(itemId: Int): String? = cache[itemId]?.takeIf { it.isNotEmpty() }

    fun isResolved(itemId: Int): Boolean = cache.containsKey(itemId)

    /**
     * Resolve [itemId]'s icon url, querying bdocodex autocomplete by [name] if
     * needed. Returns null on miss/error (caller shows the monogram). Safe to
     * call repeatedly — results are cached, in-flight lookups for the same name
     * are shared, and concurrency is capped so list scrolls don't get throttled.
     */
    suspend fun resolve(itemId: Int, name: String): String? {
        cache[itemId]?.let { return it.takeIf { v -> v.isNotEmpty() } }
        if (name.isBlank()) return null
        val term = cleanTerm(name)
        return withContext(Dispatchers.IO) {
            val resolved = runCatching { lookupShared(term) }.getOrDefault(emptyMap())
            // Only persist real hits (skip "" misses — those are cheap to re-resolve
            // and we don't want to permanently cache a transient bdocodex failure).
            if (resolved.isNotEmpty()) {
                resolved.forEach { (id, url) -> cache[id] = url }
                dirty = true
            }
            if (!cache.containsKey(itemId)) cache[itemId] = MISS
            cache[itemId]?.takeIf { it.isNotEmpty() }
        }
    }

    /** Share a single in-flight ac.php call across concurrent callers for the same term. */
    private suspend fun lookupShared(term: String): Map<Int, String> {
        val existing = inflightLock.withLock { inflight[term] }
        if (existing != null) return existing.await()
        val deferred = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
            gate.withPermit { runCatching { fetchIcons(term) }.getOrDefault(emptyMap()) }
        }
        inflightLock.withLock { inflight[term] = deferred }
        try {
            return deferred.await()
        } finally {
            inflightLock.withLock { inflight.remove(term) }
        }
    }

    private fun fetchIcons(term: String): Map<Int, String> {
        val url = "$AC_URL?l=us&term=${term.encodeUrl()}"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
            )
            .header("Referer", "https://bdocodex.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string() ?: return emptyMap()
            return parseAcIcons(body)
        }
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

/**
 * Normalize a display name to what bdocodex autocomplete indexes: drop enhancement
 * suffixes ("(PRI)", "+15"), trailing stack counts (" x10"), and parentheticals so
 * "Tungrad Earring (PRI)" still resolves to the base item's icon.
 */
internal fun cleanTerm(name: String): String =
    name
        .replace(Regex("\\s*\\([^)]*\\)"), "")        // (PRI), (DUO), …
        .replace(Regex("\\s*\\+\\d+\\b"), "")          // +15
        .replace(Regex("\\s*[xX]\\d+\\b"), "")         // x10
        .trim()
        .ifBlank { name.trim() }

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
