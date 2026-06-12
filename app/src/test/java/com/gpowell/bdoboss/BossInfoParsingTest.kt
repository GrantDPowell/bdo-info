package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.BossDrop
import com.gpowell.bdoboss.data.BossInfo
import com.gpowell.bdoboss.data.Schedule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BossInfoParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "$name missing" }
            .bufferedReader().use { it.readText() }

    private fun load(): BossInfo = json.decodeFromString(BossInfo.serializer(), loadResource("boss_info.json"))

    @Test fun `parses with 13 bosses and a disclaimer`() {
        val info = load()
        assertEquals(13, info.bosses.size)
        assertTrue(info.disclaimer.isNotBlank())
    }

    @Test fun `bundled boss info is version 2 or later`() {
        assertTrue(load().version >= 2)
    }

    @Test fun `boss names exactly match the schedule boss list`() {
        val schedule = json.decodeFromString(Schedule.serializer(), loadResource("schedule_na.json"))
        assertEquals(schedule.bosses.toSet(), load().bosses.map { it.name }.toSet())
    }

    @Test fun `every boss has 3 to 10 drops and every drop has an item and confidence`() {
        for (b in load().bosses) {
            assertTrue("${b.name} has ${b.drops.size} drops", b.drops.size in 3..10)
            for (d in b.drops) {
                assertTrue(d.item.isNotBlank())
                assertTrue(d.confidence in setOf("official", "community-estimated", "unknown"))
            }
        }
    }

    @Test fun `every declared drop icon exists in assets`() {
        for (b in load().bosses) for (d in b.drops) {
            if (d.icon.isNotBlank()) {
                val stream = javaClass.classLoader?.getResourceAsStream("item_icons/${d.icon}")
                assertTrue("missing asset for ${d.item}: ${d.icon}", stream != null)
                stream?.close()
            }
        }
    }

    @Test fun `every drop with an icon also has a positive item id`() {
        for (b in load().bosses) for (d in b.drops) {
            if (d.icon.isNotBlank()) {
                assertTrue("${b.name}/${d.item} has icon but item_id=${d.itemId}", d.itemId > 0)
            }
        }
    }

    @Test fun `codexUrl is null without an item id and a bdocodex item link with one`() {
        assertNull(BossDrop(item = "Some Pool").codexUrl)
        assertEquals(
            "https://bdocodex.com/us/item/12007/",
            BossDrop(item = "Mark of Shadow", itemId = 12007).codexUrl,
        )
        val info = load()
        for (b in info.bosses) for (d in b.drops) {
            if (d.itemId > 0) {
                assertEquals("https://bdocodex.com/us/item/${d.itemId}/", d.codexUrl)
            } else {
                assertNull(d.codexUrl)
            }
        }
    }
}
