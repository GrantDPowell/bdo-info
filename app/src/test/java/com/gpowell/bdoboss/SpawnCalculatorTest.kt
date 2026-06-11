package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.Schedule
import com.gpowell.bdoboss.data.SpawnSlot
import com.gpowell.bdoboss.domain.SpawnCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SpawnCalculatorTest {
    private val schedule = Schedule(
        region = "NA", timezone = "America/Los_Angeles", version = 1,
        bosses = listOf("Kzarka", "Vell"),
        slots = listOf(
            SpawnSlot(DayOfWeek.MONDAY, "03:00", listOf("Kzarka")),
            SpawnSlot(DayOfWeek.SUNDAY, "14:00", listOf("Vell")),
        ),
    )
    private val la = ZoneId.of("America/Los_Angeles")

    @Test fun `next spawn after now is the soonest slot`() {
        // Mon 2026-06-08 01:00 PDT → Kzarka same day 03:00 PDT
        val now = ZonedDateTime.of(2026, 6, 8, 1, 0, 0, 0, la).toInstant()
        val spawns = SpawnCalculator.upcoming(schedule, now, count = 1)
        assertEquals(listOf("Kzarka"), spawns[0].bosses)
        assertEquals(ZonedDateTime.of(2026, 6, 8, 3, 0, 0, 0, la).toInstant(), spawns[0].at)
    }

    @Test fun `slot earlier today is skipped, wraps to next week`() {
        val now = ZonedDateTime.of(2026, 6, 8, 4, 0, 0, 0, la).toInstant()
        val spawns = SpawnCalculator.upcoming(schedule, now, count = 2)
        assertEquals(listOf("Vell"), spawns[0].bosses)              // Sun 6/14
        assertEquals(listOf("Kzarka"), spawns[1].bosses)            // Mon 6/15
        assertEquals(ZonedDateTime.of(2026, 6, 15, 3, 0, 0, 0, la).toInstant(), spawns[1].at)
    }

    @Test fun `DST spring forward keeps wall-clock time`() {
        // US DST starts Sun 2026-03-08 02:00. Kzarka Mon 3/9 03:00 must be 03:00 PDT (UTC-7).
        val now = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, la).toInstant()
        val kzarka = SpawnCalculator.upcoming(schedule, now, count = 5).first { "Kzarka" in it.bosses }
        val wall = ZonedDateTime.ofInstant(kzarka.at, la)
        assertEquals(3, wall.hour)
        assertEquals(Instant.parse("2026-03-09T10:00:00Z"), kzarka.at) // 03:00 PDT = 10:00 UTC
    }

    @Test fun `count limits results and they are sorted ascending`() {
        val now = ZonedDateTime.of(2026, 6, 8, 1, 0, 0, 0, la).toInstant()
        val spawns = SpawnCalculator.upcoming(schedule, now, count = 4)
        assertEquals(4, spawns.size)
        assertTrue(spawns.zipWithNext().all { (a, b) -> a.at < b.at })
    }

    @Test fun `co-spawning slots at the same instant are merged`() {
        val merged = Schedule(
            region = "NA", timezone = "America/Los_Angeles", version = 1,
            bosses = listOf("Kzarka", "Kutum"),
            slots = listOf(
                SpawnSlot(DayOfWeek.MONDAY, "03:00", listOf("Kzarka")),
                SpawnSlot(DayOfWeek.MONDAY, "03:00", listOf("Kutum")),
            ),
        )
        val now = ZonedDateTime.of(2026, 6, 8, 1, 0, 0, 0, la).toInstant()
        val spawns = SpawnCalculator.upcoming(merged, now, count = 1)
        assertEquals(setOf("Kzarka", "Kutum"), spawns[0].bosses.toSet())
    }

    @Test fun `spawn exactly at now is excluded - strictly after contract`() {
        // now == Kzarka's Mon 03:00 slot → that occurrence is skipped, next is a week out.
        val now = ZonedDateTime.of(2026, 6, 8, 3, 0, 0, 0, la).toInstant()
        val kzarka = SpawnCalculator.upcoming(schedule, now, count = 5).first { "Kzarka" in it.bosses }
        assertEquals(ZonedDateTime.of(2026, 6, 15, 3, 0, 0, 0, la).toInstant(), kzarka.at)
    }
}
