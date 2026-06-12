package com.gpowell.bdoboss

import com.gpowell.bdoboss.data.api.Coupon
import com.gpowell.bdoboss.data.api.CouponsResponse
import com.gpowell.bdoboss.data.api.PlayerProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD: written before BdoAlertsApi implementation to verify DTO parsing.
 */
class ApiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ---- PlayerProfile parsing ----

    @Test
    fun `player profile sample parses gear score correctly`() {
        val raw = """
            {
              "family_name": "FamilyName",
              "region": "na",
              "guild": "GuildName",
              "gear_score": 723,
              "contribution_points": 450,
              "energy": 620,
              "characters": [
                {"name": "CharacterName", "class": "Lahn", "level": 66, "is_main": true}
              ],
              "life_skills": {"Cooking": "Guru 31", "Gathering": "Master 22"}
            }
        """.trimIndent()
        val profile = json.decodeFromString<PlayerProfile>(raw)
        assertEquals(723, profile.gearScore)
    }

    @Test
    fun `player profile sample parses character class name`() {
        val raw = """
            {
              "family_name": "FamilyName",
              "region": "na",
              "guild": "GuildName",
              "gear_score": 723,
              "contribution_points": 450,
              "energy": 620,
              "characters": [
                {"name": "CharacterName", "class": "Lahn", "level": 66, "is_main": true}
              ],
              "life_skills": {"Cooking": "Guru 31", "Gathering": "Master 22"}
            }
        """.trimIndent()
        val profile = json.decodeFromString<PlayerProfile>(raw)
        assertEquals("Lahn", profile.characters[0].className)
    }

    @Test
    fun `player profile sample parses is_main true`() {
        val raw = """
            {
              "family_name": "FamilyName",
              "region": "na",
              "guild": "GuildName",
              "gear_score": 723,
              "contribution_points": 450,
              "energy": 620,
              "characters": [
                {"name": "CharacterName", "class": "Lahn", "level": 66, "is_main": true}
              ],
              "life_skills": {"Cooking": "Guru 31", "Gathering": "Master 22"}
            }
        """.trimIndent()
        val profile = json.decodeFromString<PlayerProfile>(raw)
        assertTrue(profile.characters[0].isMain)
    }

    @Test
    fun `player profile sample parses life skills`() {
        val raw = """
            {
              "family_name": "FamilyName",
              "region": "na",
              "guild": "GuildName",
              "gear_score": 723,
              "contribution_points": 450,
              "energy": 620,
              "characters": [
                {"name": "CharacterName", "class": "Lahn", "level": 66, "is_main": true}
              ],
              "life_skills": {"Cooking": "Guru 31", "Gathering": "Master 22"}
            }
        """.trimIndent()
        val profile = json.decodeFromString<PlayerProfile>(raw)
        assertEquals("Guru 31", profile.lifeSkills["Cooking"])
    }

    @Test
    fun `player profile ignores unknown fields`() {
        val raw = """
            {
              "family_name": "TestFamily",
              "region": "eu",
              "guild": "",
              "gear_score": 500,
              "contribution_points": 100,
              "energy": 200,
              "characters": [],
              "life_skills": {},
              "unknown_future_field": "some value",
              "another_stray": 42
            }
        """.trimIndent()
        // Should not throw
        val profile = json.decodeFromString<PlayerProfile>(raw)
        assertEquals("TestFamily", profile.familyName)
        assertEquals(500, profile.gearScore)
    }

    // ---- Coupon parsing ----

    @Test
    fun `coupon with missing code field defaults to empty string`() {
        // The sample from the docs has no "code" field
        val raw = """
            {"rewards": "Advice of Valks (+30) x1, Cron Stone x50", "platform": "PC", "expires": "2026-03-01"}
        """.trimIndent()
        val coupon = json.decodeFromString<Coupon>(raw)
        assertEquals("", coupon.code)
        assertEquals("Advice of Valks (+30) x1, Cron Stone x50", coupon.rewards)
        assertEquals("PC", coupon.platform)
        assertEquals("2026-03-01", coupon.expires)
    }

    @Test
    fun `coupon with all fields parses correctly`() {
        val raw = """
            {"code": "BDOINFO2026", "rewards": "Cron Stone x10", "platform": "PC", "expires": "2026-12-31"}
        """.trimIndent()
        val coupon = json.decodeFromString<Coupon>(raw)
        assertEquals("BDOINFO2026", coupon.code)
    }

    @Test
    fun `coupon ignores unknown fields`() {
        val raw = """
            {"rewards": "Cron Stone x10", "platform": "PC", "expires": "2026-12-31", "stray_field": true}
        """.trimIndent()
        val coupon = json.decodeFromString<Coupon>(raw)
        assertEquals("Cron Stone x10", coupon.rewards)
    }

    @Test
    fun `coupons response parses coupons list`() {
        val raw = """
            {
              "coupons": [
                {"rewards": "Advice of Valks (+30) x1", "platform": "PC", "expires": "2026-03-01"}
              ],
              "total": 1
            }
        """.trimIndent()
        val response = json.decodeFromString<CouponsResponse>(raw)
        assertEquals(1, response.total)
        assertEquals(1, response.coupons.size)
        assertEquals("Advice of Valks (+30) x1", response.coupons[0].rewards)
    }
}
