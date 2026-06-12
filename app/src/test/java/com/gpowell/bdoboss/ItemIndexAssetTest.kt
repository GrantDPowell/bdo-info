package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.market.IndexedItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the bundled item index (assets/item_index.json — generated from
 * arsha.io's /util/db dump, trimmed of timed "(N Days)" and "[Event]" junk).
 * Reads via classloader like the other asset tests (sourceSets wires
 * src/main/assets into test resources).
 */
class ItemIndexAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): List<IndexedItem> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("item_index.json")) {
            "item_index.json not found in test resources — check sourceSets config"
        }
        val text = stream.bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(IndexedItem.serializer()), text)
    }

    @Test
    fun `bundled index parses and is large`() {
        val items = load()
        assertTrue("expected > 1000 items, got ${items.size}", items.size > 1000)
    }

    @Test
    fun `entries are well-formed`() {
        val items = load()
        assertTrue(items.all { it.id > 0 })
        assertTrue(items.all { it.name.isNotBlank() })
        assertTrue(items.all { it.grade in 0..5 })
        // ids unique
        assertEquals(items.size, items.mapTo(HashSet()) { it.id }.size)
    }

    @Test
    fun `well-known marketable items are present`() {
        val byId = load().associateBy { it.id }
        assertEquals("Memory Fragment", byId[44195]?.name)
    }
}
