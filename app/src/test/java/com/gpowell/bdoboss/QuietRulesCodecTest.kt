package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.QuietRule
import org.junit.Assert.assertEquals
import org.junit.Test

class QuietRulesCodecTest {
    @Test fun `round trips quiet rules`() {
        val rules = listOf(
            QuietRule(id = 1, label = "Weeknights", days = setOf("MONDAY", "TUESDAY"), startMin = 23 * 60, endMin = 8 * 60),
            QuietRule(id = 2, label = "Weekend lie-in", enabled = false, days = setOf("SATURDAY", "SUNDAY"), startMin = 0, endMin = 10 * 60),
        )
        val decoded = NotificationSettings.decodeQuietRules(NotificationSettings.encodeQuietRules(rules))
        assertEquals(rules, decoded)
    }

    @Test fun `decode of garbage returns empty list`() {
        assertEquals(emptyList<QuietRule>(), NotificationSettings.decodeQuietRules("not json"))
    }

    @Test fun `decode of empty string returns empty list`() {
        assertEquals(emptyList<QuietRule>(), NotificationSettings.decodeQuietRules(""))
    }

    @Test fun `defaults cover all days`() {
        val rule = QuietRule(id = 7)
        assertEquals(QuietRule.ALL_DAYS, rule.days)
        assertEquals(7, rule.days.size)
        assertEquals("Quiet hours", rule.label)
        assertEquals(23 * 60, rule.startMin)
        assertEquals(8 * 60, rule.endMin)
    }
}
