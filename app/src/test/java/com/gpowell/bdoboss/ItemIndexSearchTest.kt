package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.market.IndexedItem
import com.gpowell.bdoboss.data.market.searchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemIndexSearchTest {

    // Deliberately unsorted fixture covering all ranking tiers.
    private val items = listOf(
        IndexedItem(1, "Memory Fragment", 0),
        IndexedItem(2, "Forbidden Memory", 2),
        IndexedItem(3, "Awakened Memory Fragment", 1),
        IndexedItem(4, "Remembrance Stone", 3),       // substring "membr"? no — "memo" not present
        IndexedItem(5, "Commemorative Coin", 0),      // "memo" at word-interior (Com-memo-rative)
        IndexedItem(6, "Memoir of a Hero", 4),
        IndexedItem(7, "Cheese", 0),
        IndexedItem(8, "Cheese", 0),                  // duplicate name+grade, higher id
        IndexedItem(9, "Cheese Gratin", 1),
    )

    @Test
    fun `startsWith beats word-boundary beats contains`() {
        val results = searchIndex(items, "memo")
        val names = results.map { it.name }
        // Tier 1 (startsWith): Memoir..., Memory Fragment; Tier 2 (word start):
        // Awakened Memory Fragment, Forbidden Memory; Tier 3 (contains): Commemorative Coin.
        assertTrue(names.indexOf("Memory Fragment") < names.indexOf("Forbidden Memory"))
        assertTrue(names.indexOf("Memoir of a Hero") < names.indexOf("Awakened Memory Fragment"))
        assertTrue(names.indexOf("Forbidden Memory") < names.indexOf("Commemorative Coin"))
        assertTrue("Remembrance Stone" !in names)
    }

    @Test
    fun `shorter names rank first within the same tier`() {
        val results = searchIndex(items, "memo")
        val names = results.map { it.name }
        // Both startsWith tier: "Memoir of a Hero" (16) vs "Memory Fragment" (15).
        assertTrue(names.indexOf("Memory Fragment") < names.indexOf("Memoir of a Hero"))
    }

    @Test
    fun `search is case-insensitive`() {
        assertEquals(
            searchIndex(items, "MEMORY").map { it.id },
            searchIndex(items, "memory").map { it.id },
        )
        assertTrue(searchIndex(items, "cHeEsE").isNotEmpty())
    }

    @Test
    fun `limit is respected`() {
        val results = searchIndex(items, "e", limit = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(searchIndex(items, "").isEmpty())
        assertTrue(searchIndex(items, "   ").isEmpty())
    }

    @Test
    fun `no match returns empty list`() {
        assertTrue(searchIndex(items, "zzzzz").isEmpty())
    }

    @Test
    fun `duplicate name and grade collapses to the lowest id`() {
        val results = searchIndex(items, "cheese")
        val cheeses = results.filter { it.name == "Cheese" }
        assertEquals(1, cheeses.size)
        assertEquals(7, cheeses.first().id)
        // The non-duplicate "Cheese Gratin" still shows.
        assertTrue(results.any { it.name == "Cheese Gratin" })
    }

    @Test
    fun `exact name match works with surrounding whitespace in query`() {
        val results = searchIndex(items, "  memory fragment  ")
        assertEquals("Memory Fragment", results.first().name)
    }
}
