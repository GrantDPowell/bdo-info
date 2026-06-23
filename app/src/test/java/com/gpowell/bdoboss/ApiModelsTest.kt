package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.api.Coupon
import com.gpowell.bdoboss.data.api.CouponsResponse
import com.gpowell.bdoboss.data.api.NewsResponse
import com.gpowell.bdoboss.data.api.PlayerProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO parsing tests pinned to the REAL BDO Alerts payloads (captured 2026-06-23 with a
 * live key). The earlier version of these tests used the idealized doc examples, which do
 * NOT match production — that mismatch (life_skills array vs map, max_gear_score,
 * character_name/character_class) was the Profile-page bug. Keep these aligned to reality.
 */
class ApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ---- PlayerProfile (real shape) ----

    private val realProfile = """
        {
          "status": "cached",
          "family_name": "Jordine",
          "region": "NA",
          "guild": null,
          "max_gear_score": 723,
          "energy": 620,
          "contribution_points": 450,
          "family_created": "Apr 14, 2022 (UTC)",
          "characters": [
            {"character_name": "Adventurerae", "character_class": "Striker", "level": 66, "is_main": true}
          ],
          "life_skills": [
            {"skill_name": "Cooking", "level_rank": "Guru", "level_num": 31, "mastery": 1850},
            {"skill_name": "Gathering", "level_rank": "Beginner", "level_num": 1, "mastery": 0}
          ],
          "guild_history": []
        }
    """.trimIndent()

    @Test
    fun `profile parses max_gear_score into gearScore`() {
        assertEquals(723, json.decodeFromString<PlayerProfile>(realProfile).gearScore)
    }

    @Test
    fun `profile parses character_name and character_class`() {
        val c = json.decodeFromString<PlayerProfile>(realProfile).characters[0]
        assertEquals("Adventurerae", c.name)
        assertEquals("Striker", c.className)
        assertTrue(c.isMain)
    }

    @Test
    fun `profile parses life_skills array and formats display`() {
        val skills = json.decodeFromString<PlayerProfile>(realProfile).lifeSkills
        assertEquals(2, skills.size)
        assertEquals("Guru 31", skills[0].display)
        assertTrue(skills[0].isTrained)
        assertFalse(skills[1].isTrained) // Beginner 1 = untrained
    }

    @Test
    fun `profile tolerates null guild`() {
        assertEquals(null, json.decodeFromString<PlayerProfile>(realProfile).guild)
    }

    // ---- Coupons (real shape) ----

    @Test
    fun `coupons response parses real shape and platform`() {
        val raw = """
            {
              "total_coupons": 1,
              "coupons": [
                {"code": "THEDESERTTHNXYOU", "description": "Both", "rewards": "Cron Stone X10,000",
                 "expiry_date": null, "is_expired": false, "created_at": "2025-12-13T04:51:36"}
              ]
            }
        """.trimIndent()
        val resp = json.decodeFromString<CouponsResponse>(raw)
        assertEquals(1, resp.totalCoupons)
        assertEquals(1, resp.coupons.size)
        val c: Coupon = resp.coupons[0]
        assertEquals("THEDESERTTHNXYOU", c.code)
        assertEquals("Both", c.platform)
        assertEquals("", c.expires) // null expiry_date → blank
        assertFalse(c.isExpired)
    }

    // ---- News (real shape: wrapped in "updates") ----

    @Test
    fun `news response parses updates array`() {
        val raw = """
            {
              "total_updates": 1,
              "board_type": 3,
              "board_name": "Events",
              "updates": [
                {"title": "Desert Light", "url": "https://example.com/n",
                 "image_url": "https://cdn/x.jpg", "date_posted": "Ongoing", "board_name": "Events"}
              ]
            }
        """.trimIndent()
        val resp = json.decodeFromString<NewsResponse>(raw)
        assertEquals(1, resp.updates.size)
        assertEquals("Desert Light", resp.updates[0].title)
        assertEquals("https://example.com/n", resp.updates[0].href)
        assertEquals("Ongoing", resp.updates[0].whenText)
    }
}
