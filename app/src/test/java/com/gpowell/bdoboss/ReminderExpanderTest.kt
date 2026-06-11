package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.domain.ReminderExpander
import com.gpowell.bdoboss.domain.Spawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderExpanderTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val spawnAt = Instant.parse("2026-06-08T10:00:00Z") // 03:00 PDT
    private val spawns = listOf(Spawn(spawnAt, listOf("Kzarka", "Uturi")))

    @Test fun `expands per-boss leads into one reminder per boss per lead`() {
        val s = NotificationSettings(
            enabledBosses = setOf("Kzarka"),
            leadsByBoss = mapOf("Kzarka" to setOf(30, 5)),
        )
        val r = ReminderExpander.expand(spawns, s, now = spawnAt.minusSeconds(7200), zone = zone)
        assertEquals(2, r.size)
        assertEquals(spawnAt.minusSeconds(30 * 60), r[0].triggerAt)
        assertEquals(spawnAt.minusSeconds(5 * 60), r[1].triggerAt)
        assertTrue(r.all { it.boss == "Kzarka" })
        assertTrue(r.all { it.spawnAt == spawnAt })
        assertEquals(listOf(30, 5), r.map { it.leadMin })
    }

    @Test fun `disabled boss and master-off produce nothing`() {
        val s = NotificationSettings(enabledBosses = setOf("Karanda"))
        assertEquals(0, ReminderExpander.expand(spawns, s, spawnAt.minusSeconds(7200), zone).size)
        val off = NotificationSettings(masterEnabled = false)
        assertEquals(0, ReminderExpander.expand(spawns, off, spawnAt.minusSeconds(7200), zone).size)
    }

    @Test fun `past and exactly-now triggers are dropped`() {
        val s = NotificationSettings(enabledBosses = setOf("Kzarka"), leadsByBoss = mapOf("Kzarka" to setOf(30)))
        assertEquals(0, ReminderExpander.expand(spawns, s, now = spawnAt.minusSeconds(60), zone = zone).size)
        assertEquals(0, ReminderExpander.expand(spawns, s, now = spawnAt.minusSeconds(30 * 60), zone = zone).size)
    }

    @Test fun `quiet hours crossing midnight suppress triggers inside the window`() {
        // quiet 23:00→08:00 local; trigger at 02:30 PDT is inside
        val s = NotificationSettings(
            enabledBosses = setOf("Kzarka"),
            leadsByBoss = mapOf("Kzarka" to setOf(30)),
            quietEnabled = true, quietStartMin = 23 * 60, quietEndMin = 8 * 60,
        )
        val r = ReminderExpander.expand(spawns, s, now = spawnAt.minusSeconds(7200), zone = zone)
        assertEquals(0, r.size)
    }

    @Test fun `quiet hours non-crossing window suppresses only inside`() {
        // window 02:00-04:00; trigger 02:30 inside → dropped; with window 04:00-06:00 → kept
        val inside = NotificationSettings(
            enabledBosses = setOf("Kzarka"), leadsByBoss = mapOf("Kzarka" to setOf(30)),
            quietEnabled = true, quietStartMin = 2 * 60, quietEndMin = 4 * 60,
        )
        assertEquals(0, ReminderExpander.expand(spawns, inside, spawnAt.minusSeconds(7200), zone).size)
        val outside = inside.copy(quietStartMin = 4 * 60, quietEndMin = 6 * 60)
        assertEquals(1, ReminderExpander.expand(spawns, outside, spawnAt.minusSeconds(7200), zone).size)
    }

    @Test fun `multiple bosses in one spawn each get reminders and output is sorted`() {
        val s = NotificationSettings(
            enabledBosses = setOf("Kzarka", "Uturi"),
            leadsByBoss = mapOf("Kzarka" to setOf(10), "Uturi" to setOf(20)),
        )
        val r = ReminderExpander.expand(spawns, s, spawnAt.minusSeconds(7200), zone)
        assertEquals(2, r.size)
        assertEquals("Uturi", r[0].boss)   // 20-min lead fires earlier
        assertEquals("Kzarka", r[1].boss)
        assertTrue(r.zipWithNext().all { (a, b) -> a.triggerAt <= b.triggerAt })
    }
}
