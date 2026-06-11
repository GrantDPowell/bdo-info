package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.Schedule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParsingTest {
    private val timePattern = Regex("^\\d{2}:\\d{2}$")

    private fun load(): Schedule {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("schedule_na.json")) {
            "schedule_na.json not found in test resources — check sourceSets config"
        }
        val text = stream.bufferedReader().use { it.readText() }
        return Json.decodeFromString(Schedule.serializer(), text)
    }

    @Test fun `parses bundled schedule`() {
        val s = load()
        assertEquals("NA", s.region)
        assertEquals("America/Los_Angeles", s.timezone)
        assertEquals(13, s.bosses.size)
        assertTrue(s.slots.isNotEmpty())
        assertEquals(68, s.slots.size)
    }

    @Test fun `every slot boss is in the boss list and times are HH-mm`() {
        val s = load()
        val names = s.bosses.toSet()
        for (slot in s.slots) {
            assertTrue("unknown boss in $slot", slot.bosses.all { it in names })
            assertTrue("bad time ${slot.time}", timePattern.matches(slot.time))
        }
    }

    @Test fun `every slot day is a valid DayOfWeek name`() {
        val s = load()
        val days = java.time.DayOfWeek.entries.map { it.name }.toSet()
        assertTrue(s.slots.all { it.day in days })
    }

    @Test fun `vell spawns exactly twice a week`() {
        val s = load()
        assertEquals(2, s.slots.count { "Vell" in it.bosses })
    }
}
