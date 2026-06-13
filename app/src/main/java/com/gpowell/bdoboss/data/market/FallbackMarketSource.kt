package com.gpowell.bdoboss.data.market

import com.gpowell.bdoboss.data.api.ApiResult

/**
 * Tries [primary] first and falls back to [secondary] when primary fails
 * (Offline or HttpError) — so the Market keeps working through arsha's frequent
 * Imperva outages by switching to the blackdesertmarket mirror.
 *
 * Only the operations both sources support are bridged (prices, itemAll,
 * categoryList). [search]/[waitList]/[history] go to primary alone — the mirror
 * has no name search, wait list, or price history, so there's nothing to fall
 * back to (history simply degrades to "no history" when arsha is down).
 */
class FallbackMarketSource(
    private val primary: MarketSource,
    private val secondary: MarketSource,
) : MarketSource {

    override suspend fun prices(itemIds: List<Int>): ApiResult<List<MarketPrice>> =
        primary.prices(itemIds).orElse { secondary.prices(itemIds) }

    override suspend fun itemAll(itemId: Int): ApiResult<List<MarketPrice>> =
        primary.itemAll(itemId).orElse { secondary.itemAll(itemId) }

    override suspend fun categoryList(
        mainCategory: Int,
        subCategory: Int,
    ): ApiResult<List<MarketListing>> =
        primary.categoryList(mainCategory, subCategory)
            .orElse { secondary.categoryList(mainCategory, subCategory) }

    override suspend fun search(query: String): ApiResult<List<MarketPrice>> =
        primary.search(query)

    override suspend fun waitList(): ApiResult<List<WaitListEntry>> =
        primary.waitList()

    override suspend fun history(itemId: Int, enhancement: Int): ApiResult<List<PricePoint>> =
        primary.history(itemId, enhancement)
}

/**
 * Returns this result if it's a Success; otherwise runs [fallback] and returns
 * its result if that succeeds, else the original failure (so the primary error
 * is what surfaces when both are down).
 */
internal suspend inline fun <T> ApiResult<T>.orElse(
    fallback: () -> ApiResult<T>,
): ApiResult<T> {
    if (this is ApiResult.Success) return this
    val alt = fallback()
    return if (alt is ApiResult.Success) alt else this
}
