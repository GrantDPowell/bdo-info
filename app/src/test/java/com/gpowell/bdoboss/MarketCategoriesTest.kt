package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.market.MarketCategory
import com.gpowell.bdoboss.data.market.parseCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-parse tests plus a validation of the bundled category tree
 * (assets/market_categories.json — sourced from veliainn-market-resources, codes
 * verified against live arsha). Reads via classloader like the other asset tests.
 */
class MarketCategoriesTest {

    @Test fun `parses nested category json sorted by code`() {
        val json = """
            {
              "5": {"name":"Sub Weapon","sub_categories":{"2":{"name":"Dagger"},"1":{"name":"Shield"}}},
              "1": {"name":"Main Weapon","sub_categories":{"1":{"name":"Longsword"}}}
            }
        """.trimIndent()
        val cats = parseCategories(json)
        assertEquals(listOf(1, 5), cats.map { it.code })
        assertEquals("Main Weapon", cats[0].name)
        // subs sorted by code
        assertEquals(listOf(1, 2), cats[1].subs.map { it.code })
        assertEquals("Shield", cats[1].subs[0].name)
    }

    @Test fun `garbage input parses to empty list`() {
        assertEquals(emptyList<MarketCategory>(), parseCategories("not json"))
        assertEquals(emptyList<MarketCategory>(), parseCategories(""))
    }

    @Test fun `bundled category asset has the full market tree`() {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("market_categories.json"),
        ) { "market_categories.json not found in test resources — check sourceSets config" }
        val cats = parseCategories(stream.bufferedReader().use { it.readText() })

        assertEquals("expected 17 main categories", 17, cats.size)
        val byCode = cats.associateBy { it.code }
        assertEquals("Main Weapon", byCode[1]?.name)
        assertEquals("Armor", byCode[15]?.name)
        assertEquals("Accessory", byCode[20]?.name)
        assertEquals("Material", byCode[25]?.name)
        // Main Weapon's first sub is Longsword; every main has at least one sub.
        assertEquals("Longsword", byCode[1]?.subs?.firstOrNull { it.code == 1 }?.name)
        assertTrue(cats.all { it.subs.isNotEmpty() })
    }
}
