package com.gpowell.bdoboss.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Typed client for the entire BDO Alerts REST API (https://api.bdoalerts.net).
 *
 * Authentication: every call adds `X-API-Key` header.  A blank key short-circuits
 * to [ApiResult.NoKey] with zero network activity — wiring the key in is pure config.
 *
 * Rate limits (reference): 100 req/min, 5 000 req/day.
 * Regions: na, eu, kr, sa, asia, console_na, console_eu
 *   (market endpoints: na/eu only).
 *
 * Endpoints without published response shapes return [ApiResult]<[JsonElement]> so
 * callers get the raw tree; typed overloads are added once the real key arrives and
 * shapes are confirmed.  Decode sentinel: [ApiResult.HttpError] with code -1 means
 * the HTTP call succeeded but JSON decoding into the typed DTO failed.
 *
 * @param keyProvider  Suspend lambda that returns the stored API key (wired to
 *                     [com.gpowell.bdoboss.data.SettingsRepository.apiKey]).
 * @param client       OkHttpClient instance (injectable for tests / shared instance).
 * @param baseUrl      API root; override in tests to avoid real network.
 */
class BdoAlertsApi(
    private val keyProvider: suspend () -> String,
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.bdoalerts.net",
) {

    // -------------------------------------------------------------------------
    // Shared JSON codec — ignoreUnknownKeys so future API additions don't break
    // -------------------------------------------------------------------------
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // =========================================================================
    // Core transport
    // =========================================================================

    /**
     * Makes an authenticated GET request and returns a raw [JsonElement] on success.
     * Returns [ApiResult.NoKey] immediately (no network) when the key is blank.
     */
    private suspend fun getRaw(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): ApiResult<JsonElement> = withContext(Dispatchers.IO) {
        val key = keyProvider()
        if (key.isBlank()) return@withContext ApiResult.NoKey

        val urlBuilder = StringBuilder("$baseUrl$path")
        if (params.isNotEmpty()) {
            urlBuilder.append('?')
            urlBuilder.append(params.entries.joinToString("&") { (k, v) ->
                "${k.encodeUrl()}=${v.encodeUrl()}"
            })
        }

        val request = Request.Builder()
            .url(urlBuilder.toString())
            .addHeader("X-API-Key", key)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext ApiResult.HttpError(response.code)
            val body = response.body?.string() ?: return@withContext ApiResult.HttpError(-1)
            val element = runCatching { json.parseToJsonElement(body) }
                .getOrElse { return@withContext ApiResult.HttpError(-1) }
            ApiResult.Success(element)
        } catch (_: IOException) {
            ApiResult.Offline
        }
    }

    /** URL-encodes a single component (spaces → %20, etc.). */
    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    /**
     * Attempts to decode a [JsonElement] success into a typed [T].
     * On decode failure returns [ApiResult.HttpError] with sentinel code -1.
     */
    private inline fun <reified T> ApiResult<JsonElement>.parse(
        serializer: DeserializationStrategy<T>,
    ): ApiResult<T> = when (this) {
        is ApiResult.Success -> runCatching {
            ApiResult.Success(json.decodeFromJsonElement(serializer, data))
        }.getOrElse { ApiResult.HttpError(-1) }
        is ApiResult.NoKey -> ApiResult.NoKey
        is ApiResult.HttpError -> ApiResult.HttpError(code)
        is ApiResult.Offline -> ApiResult.Offline
    }

    // =========================================================================
    // News
    // =========================================================================

    /**
     * GET /api/news — board_type: 1=Notices 2=Updates 3=Events 4=GM Notes 5=Pearl Shop.
     * Cache hint: poll at most once per hour; content changes on patch days.
     */
    suspend fun news(boardType: Int, limit: Int? = null): ApiResult<List<NewsItem>> {
        val params = buildMap<String, String> {
            put("board_type", boardType.toString())
            if (limit != null) put("limit", limit.toString())
        }
        return getRaw("/api/news", params).parse(NewsResponse.serializer()).map { resp ->
            resp.updates
        }
    }

    // =========================================================================
    // Coupons
    // =========================================================================

    /**
     * GET /api/coupons — returns active in-game coupons.
     * Cache hint: daily refresh is sufficient; coupons rarely change intra-day.
     *
     * Unwrap strategy: deserialises [CouponsResponse] (tolerates "coupons" or "data"
     * array field names via dual-field model with defaults), then returns whichever
     * list is non-empty.  If both are empty the response total is authoritative.
     */
    suspend fun coupons(): ApiResult<List<Coupon>> {
        val raw = getRaw("/api/coupons")
        return raw.parse(CouponsResponse.serializer()).map { resp ->
            resp.coupons.filterNot { it.isExpired }
        }
    }

    // =========================================================================
    // Maintenance
    // =========================================================================

    /**
     * GET /api/maintenance-status?region= — region maintenance status.
     * Cache hint: poll at most every 5 minutes during expected windows.
     */
    suspend fun maintenanceStatus(region: String): ApiResult<MaintenanceStatus> =
        getRaw("/api/maintenance-status", mapOf("region" to region))
            .parse(MaintenanceStatus.serializer())

    /**
     * GET /api/maintenance-status/all — maintenance status for all regions.
     * Cache hint: poll at most every 5 minutes during expected windows.
     */
    suspend fun maintenanceStatusAll(): ApiResult<JsonElement> =
        getRaw("/api/maintenance-status/all")

    /**
     * GET /api/maintenance/check — upstream maintenance check.
     * Cache hint: poll at most every 5 minutes.
     */
    suspend fun maintenanceCheck(): ApiResult<JsonElement> =
        getRaw("/api/maintenance/check")

    // =========================================================================
    // Player
    // =========================================================================

    /**
     * GET /api/player/search/{region}?query= — search for players by family name prefix.
     * Cache hint: do not cache; user-initiated search.
     */
    suspend fun playerSearch(region: String, query: String): ApiResult<List<PlayerSearchResult>> =
        getRaw("/api/player/search/$region", mapOf("query" to query))
            .parse(PlayerSearchResponse.serializer())
            .map { it.results }

    /** GET /api/guild/search/{region}?query= — typed guild search. */
    suspend fun guildSearchTyped(region: String, query: String): ApiResult<List<GuildSearchResult>> =
        getRaw("/api/guild/search/$region", mapOf("query" to query))
            .parse(GuildSearchResponse.serializer())
            .map { it.results }

    /**
     * GET /api/player/{region}/{family_name}?force_refresh={bool}
     * Returns a fully typed [PlayerProfile].
     * Cache hint: cache up to 10 minutes unless force_refresh is true.
     */
    suspend fun playerProfile(
        region: String,
        familyName: String,
        forceRefresh: Boolean = false,
    ): ApiResult<PlayerProfile> {
        val params = if (forceRefresh) mapOf("force_refresh" to "true") else emptyMap()
        val raw = getRaw("/api/player/$region/${familyName.encodeUrl()}", params)
        return raw.parse(PlayerProfile.serializer())
    }

    // =========================================================================
    // Guild
    // =========================================================================

    /**
     * GET /api/guild/search/{region}?query= — search for guilds.
     * Cache hint: do not cache; user-initiated search.
     */
    suspend fun guildSearch(region: String, query: String): ApiResult<JsonElement> =
        getRaw("/api/guild/search/$region", mapOf("query" to query))

    /**
     * GET /api/guild/{region}/{guild_name}?force_refresh= — guild profile.
     * Cache hint: cache up to 10 minutes unless force_refresh is true.
     */
    suspend fun guildProfile(
        region: String,
        guildName: String,
        forceRefresh: Boolean = false,
    ): ApiResult<JsonElement> {
        val params = if (forceRefresh) mapOf("force_refresh" to "true") else emptyMap()
        return getRaw("/api/guild/$region/${guildName.encodeUrl()}", params)
    }

    // =========================================================================
    // Market — item endpoints
    // =========================================================================

    /**
     * GET /api/market/{region}/item/{item_id}?sub_key={enhancement} — single item price.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketItem(
        region: String,
        itemId: Int,
        subKey: Int? = null,
    ): ApiResult<JsonElement> {
        val params = buildMap<String, String> {
            if (subKey != null) put("sub_key", subKey.toString())
        }
        return getRaw("/api/market/$region/item/$itemId", params)
    }

    /**
     * GET /api/market/{region}/item/{item_id}/all — all enhancement levels for an item.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketItemAll(region: String, itemId: Int): ApiResult<JsonElement> =
        getRaw("/api/market/$region/item/$itemId/all")

    /**
     * GET /api/market/{region}/item-names?ids={csv} — bulk item name lookup (max 500 ids).
     * Cache hint: item names are static; cache aggressively (24h).
     */
    suspend fun marketItemNames(region: String, ids: List<Int>): ApiResult<JsonElement> =
        getRaw("/api/market/$region/item-names", mapOf("ids" to ids.joinToString(",")))

    /**
     * GET /api/market/{region}/common/{item_name} — price for common crafting materials.
     * item_name: caphras, black_stone_weapon, black_stone_armor, memory_fragment,
     *   hard_black_crystal, sharp_black_crystal, manos_stone.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketCommon(region: String, itemName: String): ApiResult<JsonElement> =
        getRaw("/api/market/$region/common/$itemName")

    // =========================================================================
    // Market — listing endpoints
    // =========================================================================

    /**
     * GET /api/market/{region}/hot?limit= — hot items.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketHot(region: String, limit: Int? = null): ApiResult<JsonElement> {
        val params = buildMap<String, String> { if (limit != null) put("limit", limit.toString()) }
        return getRaw("/api/market/$region/hot", params)
    }

    /**
     * GET /api/market/{region}/wait?limit= — items on the waiting list.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketWait(region: String, limit: Int? = null): ApiResult<JsonElement> {
        val params = buildMap<String, String> { if (limit != null) put("limit", limit.toString()) }
        return getRaw("/api/market/$region/wait", params)
    }

    /**
     * GET /api/market/{region}/popular?limit= — popular items.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketPopular(region: String, limit: Int? = null): ApiResult<JsonElement> {
        val params = buildMap<String, String> { if (limit != null) put("limit", limit.toString()) }
        return getRaw("/api/market/$region/popular", params)
    }

    /**
     * GET /api/market/{region}/search?q=&limit= — full-text item search.
     * Cache hint: do not cache; user-initiated search.
     */
    suspend fun marketSearch(
        region: String,
        query: String,
        limit: Int? = null,
    ): ApiResult<JsonElement> {
        val params = buildMap<String, String> {
            put("q", query)
            if (limit != null) put("limit", limit.toString())
        }
        return getRaw("/api/market/$region/search", params)
    }

    /**
     * GET /api/market/{region}/category/{main_cat}?sub=&limit=&offset= — category browse.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketCategory(
        region: String,
        mainCat: Int,
        sub: Int? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): ApiResult<JsonElement> {
        val params = buildMap<String, String> {
            if (sub != null) put("sub", sub.toString())
            if (limit != null) put("limit", limit.toString())
            if (offset != null) put("offset", offset.toString())
        }
        return getRaw("/api/market/$region/category/$mainCat", params)
    }

    /**
     * GET /api/market/{region}/stats — market statistics snapshot.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun marketStats(region: String): ApiResult<JsonElement> =
        getRaw("/api/market/$region/stats")

    /**
     * GET /api/market/{region}/caphras-calc?amount= — Caphras enhancement cost calculator.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketCaphrasCalc(region: String, amount: Int): ApiResult<JsonElement> =
        getRaw("/api/market/$region/caphras-calc", mapOf("amount" to amount.toString()))

    /**
     * GET /api/market/{region}/pearlshop?limit= — Pearl Shop items currently listed on market.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun marketPearlShop(region: String, limit: Int? = null): ApiResult<JsonElement> {
        val params = buildMap<String, String> { if (limit != null) put("limit", limit.toString()) }
        return getRaw("/api/market/$region/pearlshop", params)
    }

    /**
     * GET /api/market/{region}/pearlshop/available — currently available Pearl Shop items.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun marketPearlShopAvailable(region: String): ApiResult<JsonElement> =
        getRaw("/api/market/$region/pearlshop/available")

    // =========================================================================
    // Market — price history
    // =========================================================================

    /**
     * GET /api/market/price-history/{item_id}?region=&days= — price history for one item.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketPriceHistory(
        itemId: Int,
        region: String,
        days: Int? = null,
    ): ApiResult<JsonElement> {
        val params = buildMap<String, String> {
            put("region", region)
            if (days != null) put("days", days.toString())
        }
        return getRaw("/api/market/price-history/$itemId", params)
    }

    /**
     * GET /api/market/price-history?item_ids={csv}&region= — bulk price history.
     * Cache hint: cache up to 60 seconds.
     */
    suspend fun marketPriceHistoryBulk(
        itemIds: List<Int>,
        region: String,
    ): ApiResult<JsonElement> =
        getRaw(
            "/api/market/price-history",
            mapOf("item_ids" to itemIds.joinToString(","), "region" to region),
        )

    /**
     * GET /api/market/price-history/status — price-history tracking status and
     * coverage statistics. Cache hint: low-churn diagnostics; hourly is plenty.
     */
    suspend fun marketPriceHistoryStatus(): ApiResult<JsonElement> =
        getRaw("/api/market/price-history/status")

    // =========================================================================
    // Boss timers & world state
    // =========================================================================

    /**
     * GET /api/boss-timers/{region} — upcoming boss spawn times for a region.
     * Cache hint: refresh every 30 seconds (matches WebSocket push cadence).
     */
    suspend fun bossTimers(region: String): ApiResult<JsonElement> =
        getRaw("/api/boss-timers/$region")

    /**
     * GET /api/reset-timers?region= — daily/weekly reset countdowns.
     * Cache hint: refresh every 30 seconds.
     */
    suspend fun resetTimers(region: String): ApiResult<JsonElement> =
        getRaw("/api/reset-timers", mapOf("region" to region))

    // =========================================================================
    // Cave (Atoraxxion)
    // =========================================================================

    /**
     * GET /api/cave-status?region= — current Atoraxxion cave open/closed status.
     * Cache hint: refresh every 60 seconds (matches WebSocket push cadence).
     */
    suspend fun caveStatus(region: String): ApiResult<JsonElement> =
        getRaw("/api/cave-status", mapOf("region" to region))

    /**
     * GET /api/cave-history?region=&limit=&status= — historical cave open/close events.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun caveHistory(
        region: String,
        limit: Int? = null,
        status: String? = null,
    ): ApiResult<JsonElement> {
        val params = buildMap<String, String> {
            put("region", region)
            if (limit != null) put("limit", limit.toString())
            if (status != null) put("status", status)
        }
        return getRaw("/api/cave-history", params)
    }

    /**
     * GET /api/cave-history/stats?region= — aggregate cave statistics.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun caveHistoryStats(region: String): ApiResult<JsonElement> =
        getRaw("/api/cave-history/stats", mapOf("region" to region))

    /**
     * GET /api/cave-history/recent?hours= — recent cave events within N hours.
     * Cache hint: cache up to 5 minutes.
     */
    suspend fun caveHistoryRecent(hours: Int): ApiResult<JsonElement> =
        getRaw("/api/cave-history/recent", mapOf("hours" to hours.toString()))
}
