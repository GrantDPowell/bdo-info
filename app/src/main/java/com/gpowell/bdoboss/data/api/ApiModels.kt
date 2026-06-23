package com.gpowell.bdoboss.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Player profile  (GET /api/player/{region}/{family_name})
// Real shape (observed 2026-06-23) — note it differs from the doc example:
//   max_gear_score (not gear_score), character_name/character_class,
//   life_skills is an ARRAY of objects (not a map), guild is nullable.
// =============================================================================

@Serializable
data class PlayerCharacter(
    @SerialName("character_name") val name: String = "",
    @SerialName("character_class") val className: String = "",
    val level: Int = 0,
    @SerialName("is_main") val isMain: Boolean = false,
)

@Serializable
data class LifeSkill(
    @SerialName("skill_name") val skill: String = "",
    @SerialName("level_rank") val rank: String = "",
    @SerialName("level_num") val levelNum: Int = 0,
    val mastery: Int = 0,
) {
    /** e.g. "Guru 31" — rank + tier, hiding the redundant number when it's 0. */
    val display: String get() = if (levelNum > 0) "$rank $levelNum" else rank
    /** True for skills the player has actually trained past Beginner 1. */
    val isTrained: Boolean get() = !(rank.equals("Beginner", true) && levelNum <= 1)
}

@Serializable
data class PlayerProfile(
    @SerialName("family_name") val familyName: String = "",
    val region: String = "",
    val guild: String? = null,
    @SerialName("max_gear_score") val gearScore: Int = 0,
    @SerialName("contribution_points") val contributionPoints: Int = 0,
    val energy: Int = 0,
    @SerialName("family_created") val familyCreated: String = "",
    val characters: List<PlayerCharacter> = emptyList(),
    @SerialName("life_skills") val lifeSkills: List<LifeSkill> = emptyList(),
)

// =============================================================================
// Coupons  (GET /api/coupons)
//   { total_coupons, coupons:[{ code, description, rewards, expiry_date,
//     is_expired, created_at }] }   description = "PC" | "Console" | "Both"
// =============================================================================

@Serializable
data class Coupon(
    val code: String = "",
    val rewards: String = "",
    val description: String = "",
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("is_expired") val isExpired: Boolean = false,
) {
    /** Platform label ("PC"/"Console"/"Both"). */
    val platform: String get() = description
    /** Expiry text, or blank when the coupon has no published expiry. */
    val expires: String get() = expiryDate.orEmpty()
}

@Serializable
data class CouponsResponse(
    val coupons: List<Coupon> = emptyList(),
    @SerialName("total_coupons") val totalCoupons: Int = 0,
)

// =============================================================================
// News  (GET /api/news?board_type=1..5)
//   { total_updates, board_name, updates:[{ title, url, image_url,
//     date_posted, description, board_name }] }
// =============================================================================

@Serializable
data class NewsItem(
    val title: String = "",
    val url: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("date_posted") val datePosted: String = "",
    val description: String = "",
    @SerialName("board_name") val boardName: String = "",
) {
    val href: String get() = url
    val whenText: String get() = datePosted
}

@Serializable
data class NewsResponse(
    val updates: List<NewsItem> = emptyList(),
    @SerialName("total_updates") val totalUpdates: Int = 0,
)

// =============================================================================
// Maintenance  (GET /api/maintenance-status?region=)
//   { region, in_maintenance, started_at, ends_at, time_remaining, message }
// =============================================================================

@Serializable
data class MaintenanceStatus(
    val region: String = "",
    @SerialName("in_maintenance") val inMaintenance: Boolean = false,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val message: String = "",
) {
    val active: Boolean get() = inMaintenance
    /** No reliable idle countdown field; shown only while in maintenance via [nextMaintenance]. */
    val countdown: String get() = ""
    val nextMaintenance: String get() = if (inMaintenance) endsAt.orEmpty() else ""
}

// =============================================================================
// Player & guild search
// =============================================================================

@Serializable
data class PlayerSearchResult(
    @SerialName("family_name") val familyName: String = "",
    @SerialName("profile_target") val profileTarget: String = "",
    val guild: String? = null,
    val region: String = "",
)

@Serializable
data class PlayerSearchResponse(
    val results: List<PlayerSearchResult> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class GuildSearchResult(
    @SerialName("guild_name") val guildName: String = "",
    @SerialName("guild_master") val guildMaster: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
)

@Serializable
data class GuildSearchResponse(
    val results: List<GuildSearchResult> = emptyList(),
    val total: Int = 0,
)
