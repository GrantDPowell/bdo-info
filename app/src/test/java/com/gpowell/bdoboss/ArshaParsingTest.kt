package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.market.MarketListing
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.data.market.PricePoint
import com.gpowell.bdoboss.data.market.WaitListEntry
import com.gpowell.bdoboss.data.market.parseHistory
import com.gpowell.bdoboss.data.market.parseMarketListings
import com.gpowell.bdoboss.data.market.parseMarketPrices
import com.gpowell.bdoboss.data.market.parseSearchPrices
import com.gpowell.bdoboss.data.market.parseWaitList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests against REAL arsha.io v2 payloads captured 2026-06-12
 * (json fixtures under src/test/resources/arsha — trimmed, values untouched).
 */
class ArshaParsingTest {

    private fun fixture(name: String) = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("arsha/$name")) { "$name missing" }
            .bufferedReader().use { it.readText() },
    )

    // ---- /v2/{region}/item -------------------------------------------------

    @Test fun `single item object parses to one MarketPrice`() {
        val prices = parseMarketPrices(fixture("item_single.json"))
        assertEquals(
            listOf(
                MarketPrice(
                    itemId = 44195,
                    enhancement = 0,
                    name = "Memory Fragment",
                    basePrice = 1_490_000,
                    stock = 7_498,
                    lastSoldPrice = 1_550_000,
                    lastSoldAt = 1_781_295_578,
                    totalTrades = 682_271_935,
                    priceMin = 75_000,
                    priceMax = 5_000_000,
                    minEnhance = 0,
                    maxEnhance = 0,
                ),
            ),
            prices,
        )
    }

    @Test fun `batched item array parses every row`() {
        val prices = parseMarketPrices(fixture("item_multi.json"))
        assertEquals(2, prices.size)
        assertEquals(44195, prices[0].itemId)
        assertEquals("Black Stone", prices[1].name)
        assertEquals(276_000, prices[1].basePrice)
        assertEquals(13_972, prices[1].stock)
    }

    @Test fun `unlisted item parses with zero price and stock`() {
        val prices = parseMarketPrices(fixture("item_unlisted.json"))
        assertEquals(1, prices.size)
        assertEquals(44915, prices[0].itemId)
        assertEquals(0L, prices[0].basePrice)
        assertEquals(0L, prices[0].stock)
    }

    // ---- /v2/{region}/GetWorldMarketSubList ---------------------------------

    @Test fun `sublist parses one row per enhancement level with sid as enhancement`() {
        val prices = parseMarketPrices(fixture("item_all.json"))
        assertEquals(3, prices.size)
        assertEquals(listOf(0, 1, 5), prices.map { it.enhancement })
        assertTrue(prices.all { it.itemId == 11653 && it.name == "Deboreka Necklace" })
        assertEquals(124_000_000_000, prices[2].basePrice)
        assertEquals(3L, prices[2].stock)
    }

    // ---- /v2/{region}/search (id-based, slim row) ----------------------------

    @Test fun `search row parses slim shape with enhancement 0 and no lastSold`() {
        val prices = parseSearchPrices(fixture("search.json"))
        assertEquals(
            listOf(
                MarketPrice(
                    itemId = 44195,
                    enhancement = 0,
                    name = "Memory Fragment",
                    basePrice = 1_820_000,
                    stock = 0,
                    totalTrades = 949_683_671,
                ),
            ),
            prices,
        )
    }

    // ---- /v2/{region}/GetWorldMarketList (category listing) ------------------

    @Test fun `category list parses rows with main and sub category`() {
        val listings = parseMarketListings(fixture("marketlist.json"))
        assertEquals(2, listings.size)
        assertEquals(
            MarketListing(
                itemId = 12001,
                name = "Yuria Ring",
                stock = 13,
                totalTrades = 543_487,
                basePrice = 62_000,
                mainCategory = 20,
                subCategory = 1,
            ),
            listings[0],
        )
        assertEquals("Bares Ring", listings[1].name)
        assertEquals(20, listings[1].mainCategory)
    }

    // ---- /v2/{region}/GetWorldMarketWaitList --------------------------------

    @Test fun `waitlist parses entries including high sid values`() {
        val entries = parseWaitList(fixture("waitlist.json"))
        assertEquals(4, entries.size)
        assertEquals(
            WaitListEntry(
                itemId = 719898,
                enhancement = 3,
                name = "Fallen God's Armor",
                price = 61_000_000_000,
                liveAt = 1_781_296_954,
            ),
            entries[0],
        )
        assertEquals(20, entries[3].enhancement) // Blackstar PEN is sid 20
    }

    // ---- /v2/{region}/GetMarketPriceInfo ------------------------------------

    @Test fun `history parses millis keys to epoch-second points sorted ascending`() {
        val points = parseHistory(fixture("history.json"))
        assertEquals(5, points.size)
        // First key in the fixture is intentionally out of order; output is sorted.
        assertEquals(PricePoint(at = 1_773_446_400, price = 1_742_763), points[0])
        assertEquals(PricePoint(at = 1_773_792_000, price = 1_683_696), points[4])
        assertTrue(points.zipWithNext().all { (a, b) -> a.at < b.at })
    }
}
