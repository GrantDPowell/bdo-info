package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.ui.market.WatchSort
import com.gpowell.bdoboss.ui.market.formatCompact
import com.gpowell.bdoboss.ui.market.relativeTime
import com.gpowell.bdoboss.ui.market.sortWatchlist
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketLogicTest {

    @Test fun `formatCompact scales by magnitude`() {
        assertEquals("543", formatCompact(543))
        assertEquals("1.6K", formatCompact(1_630))
        assertEquals("1.6M", formatCompact(1_630_399))
        assertEquals("682.3M", formatCompact(682_271_935))
        assertEquals("2.4B", formatCompact(2_407_191_271))
    }

    @Test fun `relativeTime buckets seconds to coarse units`() {
        val now = 1_000_000_000L
        assertEquals("—", relativeTime(0, now))
        assertEquals("just now", relativeTime(now - 10, now))
        assertEquals("5m ago", relativeTime(now - 300, now))
        assertEquals("3h ago", relativeTime(now - 3 * 3600, now))
        assertEquals("2d ago", relativeTime(now - 2 * 86_400, now))
        assertEquals("1mo ago", relativeTime(now - 40L * 86_400, now))
    }

    private fun item(id: Int, title: String) =
        Favorite(id = id.toLong(), type = FavoriteType.ITEM, title = title, itemId = id)

    private fun price(id: Int, base: Long, trades: Long) =
        MarketPrice(itemId = id, enhancement = 0, name = "", basePrice = base, stock = 0, totalTrades = trades)

    @Test fun `sortWatchlist orders by chosen key, unpriced last`() {
        val list = listOf(item(1, "Banana"), item(2, "apple"), item(3, "Cherry"))
        val prices = mapOf(
            1 to price(1, base = 500, trades = 10),
            2 to price(2, base = 900, trades = 5),
            // id 3 has no live price
        )

        // RECENT keeps repo order.
        assertEquals(listOf(1, 2, 3), sortWatchlist(list, prices, WatchSort.RECENT).map { it.itemId })
        // NAME is case-insensitive alphabetical.
        assertEquals(listOf(2, 1, 3), sortWatchlist(list, prices, WatchSort.NAME).map { it.itemId })
        // PRICE descending, unpriced (id 3) sinks to the bottom.
        assertEquals(listOf(2, 1, 3), sortWatchlist(list, prices, WatchSort.PRICE).map { it.itemId })
        // TRADES descending, unpriced last.
        assertEquals(listOf(1, 2, 3), sortWatchlist(list, prices, WatchSort.TRADES).map { it.itemId })
    }
}
