package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.QuietRule
import com.gpowell.bdoboss.domain.Reminder
import com.gpowell.bdoboss.domain.ReminderExpander
import com.gpowell.bdoboss.domain.Spawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderExpanderTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val spawnAt = Instant.parse("2026-06-08T10:00:00Z") // Monday 03:00 PDT
    private val spawns = listOf(Spawn(spawnAt, listOf("Kzarka", "Uturi")))

    private fun base(rules: List<QuietRule>) = NotificationSettings(
        enabledBosses = setOf("Kzarka"),
        leadsByBoss = mapOf("Kzarka" to setOf(30)),
        quietRules = rules,
    )

    private fun expand(s: NotificationSettings): List<Reminder> =
        ReminderExpander.expand(spawns, s, now = spawnAt.minusSeconds(7200), zone = zone)

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

    @Test fun `rule suppresses trigger on a covered day`() {
        // spawnAt 2026-06-08T10:00Z = Monday 03:00 PDT; lead 30 → trigger Mon 02:30 PDT
        val s = base(rules = listOf(QuietRule(1, days = setOf("MONDAY"), startMin = 0, endMin = 8 * 60)))
        assertEquals(0, expand(s).size)
    }

    @Test fun `rule does not suppress on uncovered day`() {
        val s = base(rules = listOf(QuietRule(1, days = setOf("TUESDAY"), startMin = 0, endMin = 8 * 60)))
        assertEquals(1, expand(s).size)
    }

    @Test fun `midnight-crossing rule is attributed to its start day`() {
        // SUNDAY 23:00→08:00 covers Monday 02:30 (started Sunday night); MONDAY 23:00→08:00 does NOT
        val sunday = base(rules = listOf(QuietRule(1, days = setOf("SUNDAY"), startMin = 23 * 60, endMin = 8 * 60)))
        assertEquals(0, expand(sunday).size)
        val monday = base(rules = listOf(QuietRule(1, days = setOf("MONDAY"), startMin = 23 * 60, endMin = 8 * 60)))
        assertEquals(1, expand(monday).size)
    }

    @Test fun `disabled rule and empty days are ignored`() {
        val s = base(
            rules = listOf(
                QuietRule(1, enabled = false, days = setOf("MONDAY"), startMin = 0, endMin = 8 * 60),
                QuietRule(2, days = emptySet(), startMin = 0, endMin = 24 * 60 - 1),
            ),
        )
        assertEquals(1, expand(s).size)
    }

    @Test fun `any one of multiple rules suppresses`() {
        val s = base(
            rules = listOf(
                QuietRule(1, days = setOf("FRIDAY"), startMin = 0, endMin = 60),
                QuietRule(2, days = setOf("MONDAY"), startMin = 2 * 60, endMin = 3 * 60),
            ),
        )
        assertEquals(0, expand(s).size)
    }
}
