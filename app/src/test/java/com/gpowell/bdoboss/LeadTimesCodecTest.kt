package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LeadTimesCodecTest {
    @Test fun `round trips leads map`() {
        val leads = mapOf("Kzarka" to setOf(30, 5), "Vell" to setOf(60))
        val decoded = NotificationSettings.decodeLeads(NotificationSettings.encodeLeads(leads))
        assertEquals(leads, decoded)
    }

    @Test fun `decode of garbage returns empty map`() {
        assertEquals(emptyMap<String, Set<Int>>(), NotificationSettings.decodeLeads("not json"))
    }

    @Test fun `default lead applies when boss missing`() {
        val s = NotificationSettings(enabledBosses = setOf("Kzarka"), leadsByBoss = emptyMap())
        assertEquals(setOf(15), s.leadsFor("Kzarka"))
    }
}
