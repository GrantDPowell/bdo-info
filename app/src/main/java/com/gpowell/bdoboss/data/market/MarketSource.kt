package com.gpowell.bdoboss.data.market

import com.gpowell.bdoboss.data.api.ApiResult

/**
 * One marketplace listing row — a single (item, enhancement) pair.
 *
 * Field mapping verified against live arsha.io v2 payloads (2026-06-12):
 * `{"name":"Memory Fragment","id":44195,"sid":0,"basePrice":1490000,
 *   "currentStock":7498,"lastSoldPrice":1550000,"lastSoldTime":1781295578,...}`
 * arsha calls the enhancement level `sid` and timestamps are epoch seconds.
 */
data class MarketPrice(
    val itemId: Int,
    val enhancement: Int,
    val name: String,
    val basePrice: Long,
    val stock: Long,
    val lastSoldPrice: Long = 0,
    val lastSoldAt: Long = 0, // epoch seconds
)

/**
 * One marketplace waiting-list entry (item registered, goes live at [liveAt]).
 * arsha shape: `{"name":"Deboreka Necklace","id":11653,"sid":4,
 *   "price":16500000000,"liveAt":1781296987}`
 */
data class WaitListEntry(
    val itemId: Int,
    val enhancement: Int,
    val name: String,
    val price: Long,
    val liveAt: Long, // epoch seconds
)

/** One point of daily price history. [at] is epoch seconds. */
data class PricePoint(val at: Long, val price: Long)

/**
 * Abstraction over a BDO central-market data provider.
 * Current implementation: [ArshaSource] (keyless). A keyed BdoAlertsSource backed
 * by [com.gpowell.bdoboss.data.api.BdoAlertsApi] slots in once the API key lands.
 */
interface MarketSource {
    /** Base (enhancement 0) price + stock for each id, batched in one call where possible. */
    suspend fun prices(itemIds: List<Int>): ApiResult<List<MarketPrice>>

    /** Every enhancement level (sid) of a single item. */
    suspend fun itemAll(itemId: Int): ApiResult<List<MarketPrice>>

    /**
     * Item search. NOTE: arsha.io has NO name search — its /search endpoint is
     * id-based only — so name queries return [ApiResult.HttpError] 404 there.
     * See [ArshaSource.search] for details.
     */
    suspend fun search(query: String): ApiResult<List<MarketPrice>>

    /** Items currently on the marketplace waiting list. */
    suspend fun waitList(): ApiResult<List<WaitListEntry>>

    /** Daily price history (~90 days) for one (item, enhancement). */
    suspend fun history(itemId: Int, enhancement: Int = 0): ApiResult<List<PricePoint>>
}
