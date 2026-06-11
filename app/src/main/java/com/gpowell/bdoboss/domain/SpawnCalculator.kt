package com.gpowell.bdoboss.domain

import com.gpowell.bdoboss.data.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class Spawn(val at: Instant, val bosses: List<String>)

object SpawnCalculator {
    /** Next [count] spawns strictly after [now], merged per instant, ascending. */
    fun upcoming(schedule: Schedule, now: Instant, count: Int): List<Spawn> {
        val zone = ZoneId.of(schedule.timezone)
        val today = ZonedDateTime.ofInstant(now, zone).toLocalDate()
        val byInstant = sortedMapOf<Instant, MutableList<String>>()
        // 15 days guarantees ≥2 occurrences of weekly slots even across DST shifts.
        for (offset in 0..15L) {
            val date: LocalDate = today.plusDays(offset)
            val dow: DayOfWeek = date.dayOfWeek
            for (slot in schedule.slots) {
                if (slot.day != dow) continue
                val at = ZonedDateTime.of(date, LocalTime.parse(slot.time), zone).toInstant()
                if (at > now) byInstant.getOrPut(at) { mutableListOf() }.addAll(slot.bosses)
            }
        }
        return byInstant.entries.take(count).map { Spawn(it.key, it.value.toList()) }
    }
}
