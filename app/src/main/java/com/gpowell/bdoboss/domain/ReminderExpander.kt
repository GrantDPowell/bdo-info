package com.gpowell.bdoboss.domain

import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.QuietRule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class Reminder(val triggerAt: Instant, val boss: String, val spawnAt: Instant, val leadMin: Int)

object ReminderExpander {
    /** Sorted reminders strictly after [now]; triggers covered by any quiet rule dropped. [zone] = device zone. */
    fun expand(
        spawns: List<Spawn>,
        settings: NotificationSettings,
        now: Instant,
        zone: ZoneId,
    ): List<Reminder> {
        if (!settings.masterEnabled) return emptyList()
        val out = mutableListOf<Reminder>()
        for (spawn in spawns) {
            for (boss in spawn.bosses) {
                if (boss !in settings.enabledBosses) continue
                for (lead in settings.leadsFor(boss)) {
                    val trigger = spawn.at.minusSeconds(lead * 60L)
                    if (trigger <= now) continue
                    if (isQuiet(trigger, settings.quietRules, zone)) continue
                    out += Reminder(trigger, boss, spawn.at, lead)
                }
            }
        }
        return out.sortedBy { it.triggerAt }
    }

    /** True when ANY enabled rule covers [at] in [zone]. */
    private fun isQuiet(at: Instant, rules: List<QuietRule>, zone: ZoneId): Boolean {
        if (rules.isEmpty()) return false
        val local = ZonedDateTime.ofInstant(at, zone)
        val day = local.dayOfWeek
        val prevDay = day.minus(1)
        val min = local.hour * 60 + local.minute
        return rules.any { r -> r.enabled && r.covers(day.name, prevDay.name, min) }
    }

    /**
     * A midnight-crossing window belongs to the day it STARTS: "MONDAY 23:00→08:00"
     * suppresses Tue 03:00. Empty days or zero-length windows cover nothing.
     */
    private fun QuietRule.covers(dayName: String, prevDayName: String, min: Int): Boolean = when {
        days.isEmpty() || startMin == endMin -> false
        startMin < endMin -> dayName in days && min in startMin until endMin
        else -> (dayName in days && min >= startMin) || (prevDayName in days && min < endMin)
    }
}
