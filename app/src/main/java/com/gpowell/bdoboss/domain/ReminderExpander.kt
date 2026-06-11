package com.gpowell.bdoboss.domain

import com.gpowell.bdoboss.data.NotificationSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class Reminder(val triggerAt: Instant, val boss: String, val spawnAt: Instant, val leadMin: Int)

object ReminderExpander {
    /** Sorted reminders strictly after [now]; quiet-hours triggers dropped. [zone] = device zone. */
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
                    if (settings.quietEnabled && inQuietWindow(trigger, settings, zone)) continue
                    out += Reminder(trigger, boss, spawn.at, lead)
                }
            }
        }
        return out.sortedBy { it.triggerAt }
    }

    private fun inQuietWindow(at: Instant, s: NotificationSettings, zone: ZoneId): Boolean {
        val local = ZonedDateTime.ofInstant(at, zone)
        val min = local.hour * 60 + local.minute
        return if (s.quietStartMin <= s.quietEndMin) {
            min in s.quietStartMin until s.quietEndMin
        } else {
            min >= s.quietStartMin || min < s.quietEndMin   // crosses midnight
        }
    }
}
