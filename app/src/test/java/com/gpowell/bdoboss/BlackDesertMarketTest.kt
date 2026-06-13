package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.market.FallbackMarketSource
import com.gpowell.bdoboss.data.market.MarketListing
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.data.market.MarketSource
import com.gpowell.bdoboss.data.market.PricePoint
import com.gpowell.bdoboss.data.market.WaitListEntry
import com.gpowell.bdoboss.data.market.parseBdmItems
import com.gpowell.bdoboss.data.market.parseBdmListings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BlackDesertMarketTest {

    private fun el(s: String) = Json.parseToJsonElement(s)

    @Test fun `parseBdmItems maps every enhancement row`() {
        val data = el(
            """[{"id":11653,"name":"Deboreka Necklace","count":285,"grade":3,""" +
                """"basePrice":282000000,"enhancement":0,"tradeCount":658922},""" +
                """{"id":11653,"name":"Deboreka Necklace","count":30,"grade":3,""" +
                """"basePrice":685000000,"enhancement":1,"tradeCount":45678}]""",
        )
        val rows = parseBdmItems(data)
        assertEquals(2, rows.size)
        assertEquals(
            MarketPrice(
                itemId = 11653, enhancement = 0, name = "Deboreka Necklace",
                basePrice = 282_000_000, stock = 285, totalTrades = 658_922,
            ),
            rows[0],
        )
        assertEquals(1, rows[1].enhancement)
        assertEquals(685_000_000, rows[1].basePrice)
    }

    @Test fun `parseBdmListings stamps the main and sub category`() {
        val data = el("""[{"id":12001,"name":"Yuria Ring","count":13,"grade":1,"basePrice":62000}]""")
        val rows = parseBdmListings(data, mainCategory = 20, subCategory = 1)
        assertEquals(
            MarketListing(
                itemId = 12001, name = "Yuria Ring", stock = 13, totalTrades = 0,
                basePrice = 62_000, mainCategory = 20, subCategory = 1,
            ),
            rows[0],
        )
    }

    // --- Fallback routing -----------------------------------------------------

    /** Configurable fake that records how many times prices() ran. */
    private class FakeSource(
        private val priceResult: ApiResult<List<MarketPrice>>,
    ) : MarketSource {
        var priceCalls = 0
        override suspend fun prices(itemIds: List<Int>): ApiResult<List<MarketPrice>> {
            priceCalls++
            return priceResult
        }
        override suspend fun itemAll(itemId: Int) = priceResult
        override suspend fun categoryList(mainCategory: Int, subCategory: Int) =
            ApiResult.Success(emptyList<MarketListing>())
        override suspend fun search(query: String) = priceResult
        override suspend fun waitList() = ApiResult.Success(emptyList<WaitListEntry>())
        override suspend fun history(itemId: Int, enhancement: Int) =
            ApiResult.Success(emptyList<PricePoint>())
    }

    private val sample = listOf(MarketPrice(1, 0, "X", 100, 5))

    @Test fun `fallback uses secondary only when primary fails`() = runTest {
        val primary = FakeSource(ApiResult.Offline)
        val secondary = FakeSource(ApiResult.Success(sample))
        val fb = FallbackMarketSource(primary, secondary)

        assertEquals(ApiResult.Success(sample), fb.prices(listOf(1)))
        assertEquals(1, primary.priceCalls)
        assertEquals(1, secondary.priceCalls)
    }

    @Test fun `fallback skips secondary when primary succeeds`() = runTest {
        val primary = FakeSource(ApiResult.Success(sample))
        val secondary = FakeSource(ApiResult.Success(emptyList()))
        val fb = FallbackMarketSource(primary, secondary)

        assertEquals(ApiResult.Success(sample), fb.prices(listOf(1)))
        assertEquals(0, secondary.priceCalls)
    }

    @Test fun `fallback returns primary error when both fail`() = runTest {
        val primary = FakeSource(ApiResult.HttpError(500))
        val secondary = FakeSource(ApiResult.Offline)
        val fb = FallbackMarketSource(primary, secondary)

        assertEquals(ApiResult.HttpError(500), fb.prices(listOf(1)))
    }
}
