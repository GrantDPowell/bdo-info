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

@Serializable
data class GuildProfile(
    @SerialName("guild_name") val guildName: String = "",
    val region: String = "",
    @SerialName("guild_master") val guildMaster: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
    val members: List<String> = emptyList(),
)

// =============================================================================
// Reset timers  (GET /api/reset-timers?region=)
//   each entry: { next_reset, time_until{...}, countdown:"16m 23s" }
// =============================================================================

@Serializable
data class ResetEntry(
    @SerialName("next_reset") val nextReset: String = "",
    val countdown: String = "",
)

@Serializable
data class ResetTimers(
    @SerialName("daily_reset") val daily: ResetEntry? = null,
    @SerialName("weekly_reset") val weekly: ResetEntry? = null,
    @SerialName("blackshrine_reset") val blackShrine: ResetEntry? = null,
    @SerialName("imperial_delivery") val imperial: ResetEntry? = null,
    @SerialName("trade_reset") val trade: ResetEntry? = null,
    @SerialName("barter_reset") val barter: ResetEntry? = null,
) {
    /** Ordered (label, entry) pairs for display, skipping any the API omitted. */
    fun rows(): List<Pair<String, ResetEntry>> = listOfNotNull(
        daily?.let { "Daily" to it },
        weekly?.let { "Weekly" to it },
        blackShrine?.let { "Black Shrine" to it },
        imperial?.let { "Imperial Delivery" to it },
        trade?.let { "Trade" to it },
        barter?.let { "Barter" to it },
    )
}

// =============================================================================
// Golden Pig Cave  (GET /api/cave-status?region= , /api/cave-history/stats?region=)
// =============================================================================

@Serializable
data class CaveStatus(
    val region: String = "",
    val status: String = "",          // "OPEN" / "CLOSED"
    val message: String = "",
    @SerialName("last_checked") val lastChecked: String = "",
) {
    val isOpen: Boolean get() = status.equals("OPEN", true)
}

@Serializable
data class CaveStatEntry(
    @SerialName("total_changes") val totalChanges: Int = 0,
    @SerialName("total_opens") val totalOpens: Int = 0,
    @SerialName("total_closes") val totalCloses: Int = 0,
    @SerialName("last_status") val lastStatus: String = "",
    @SerialName("last_change") val lastChange: String = "",
)

@Serializable
data class CaveStatsResponse(
    val stats: Map<String, CaveStatEntry> = emptyMap(),
)

// =============================================================================
// Weekly boss schedule  (GET /api/boss-schedule/{region})
//   { "Monday":[{time:"00:00", bosses:["Kzarka",...]}], "Tuesday":[...], ... }
// =============================================================================

@Serializable
data class ScheduleSlot(val time: String = "", val bosses: List<String> = emptyList())

// =============================================================================
// Leaderboards  (GET /api/leaderboard/gear-score , /api/leaderboard/life-skills)
// =============================================================================

@Serializable
data class GearLeader(
    val rank: Int = 0,
    @SerialName("family_name") val familyName: String = "",
    @SerialName("guild_name") val guildName: String? = null,
    val region: String = "",
    @SerialName("gear_score") val gearScore: Int = 0,
    @SerialName("main_class") val mainClass: String = "",
)

@Serializable
data class GearLeaderboard(val leaderboard: List<GearLeader> = emptyList())

@Serializable
data class LifeLeader(
    val rank: Int = 0,
    @SerialName("skill_name") val skillName: String = "",
    @SerialName("family_name") val familyName: String = "",
    @SerialName("guild_name") val guildName: String? = null,
    val region: String = "",
    @SerialName("skill_rank") val skillRank: String = "",
)

@Serializable
data class LifeLeaderboard(val leaderboard: List<LifeLeader> = emptyList())

// =============================================================================
// Grind spots  (GET /api/best-grindspots — folds in monster-ap-caps)
// =============================================================================

@Serializable
data class GrindSpot(
    val name: String = "",
    val rank: Int = 0,
    @SerialName("silver_per_hour") val silverPerHour: String = "",
    @SerialName("ap_requirement") val apReq: String = "",
    @SerialName("dp_requirement") val dpReq: String = "",
    @SerialName("cap_ap") val capAp: Int = 0,
    @SerialName("notable_drops") val notableDrops: List<String> = emptyList(),
)

@Serializable
data class GrindSpotsResp(@SerialName("grind_spots") val grindSpots: List<GrindSpot> = emptyList())

// =============================================================================
// Cron costs  (GET /api/cron-costs)
// =============================================================================

@Serializable
data class CronItem(
    val name: String = "",
    val grade: Int = 0,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("cron_costs") val cronCosts: Map<String, Int?> = emptyMap(),
)

@Serializable
data class CronCostsResp(
    @SerialName("equipment_types") val equipmentTypes: List<String> = emptyList(),
    val data: Map<String, List<CronItem>> = emptyMap(),
)

// =============================================================================
// Lightstone sets  (GET /api/lightstone-data)
// =============================================================================

@Serializable
data class LightstoneReq(val type: String = "", val stones: List<String> = emptyList())

@Serializable
data class LightstoneBonusTier(val tier: String = "", val bonuses: Map<String, String> = emptyMap())

@Serializable
data class LightstoneSet(
    val name: String = "",
    @SerialName("is_special") val isSpecial: Boolean = false,
    val requirements: List<LightstoneReq> = emptyList(),
    @SerialName("bonus_tiers") val bonusTiers: List<LightstoneBonusTier> = emptyList(),
)

@Serializable
data class LightstoneResp(@SerialName("combat_sets") val combatSets: List<LightstoneSet> = emptyList())

// =============================================================================
// Class skills  (GET /api/skills/{class})
// =============================================================================

@Serializable
data class GameSkill(
    val id: Int = 0,
    val name: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
)

@Serializable
data class ClassSkills(
    @SerialName("class_name") val className: String = "",
    @SerialName("total_skills") val totalSkills: Int = 0,
    val skills: List<GameSkill> = emptyList(),
)

// =============================================================================
// Tier lists & guides  (GET /api/tier-lists , /api/guides)
// =============================================================================

@Serializable
data class TierList(
    val id: Int = 0,
    val username: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("tier_type") val tierType: String = "",
    @SerialName("share_code") val shareCode: String = "",
    val views: Int = 0,
)

@Serializable
data class TierListsResp(@SerialName("tier_lists") val tierLists: List<TierList> = emptyList())

@Serializable
data class Guide(
    val id: String = "",
    @SerialName("author_name") val author: String = "",
    @SerialName("class_name") val className: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("guide_type") val guideType: String = "",
    val tags: List<String> = emptyList(),
    val views: Int = 0,
    val likes: Int = 0,
)

@Serializable
data class GuidesResp(val guides: List<Guide> = emptyList())

// =============================================================================
// Hot market items  (GET /api/market/{region}/hot) — curated, works keyless of scrape
// =============================================================================

@Serializable
data class HotItem(
    @SerialName("item_id") val itemId: Int = 0,
    @SerialName("sub_key") val subKey: Int = 0,
    val name: String = "",
    val price: Long = 0,
    val stock: Long = 0,
    @SerialName("total_trades") val totalTrades: Long = 0,
    @SerialName("price_change_direction") val direction: String = "",
)

@Serializable
data class HotResp(val items: List<HotItem> = emptyList())

// =============================================================================
// Streamers  (GET /api/streamers , /api/streamers/live)
// =============================================================================

@Serializable
data class Streamer(
    val username: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("stream_title") val streamTitle: String? = null,
    @SerialName("game_name") val gameName: String? = null,
)

@Serializable
data class StreamersResp(
    val streamers: List<Streamer> = emptyList(),
    @SerialName("total_viewers") val totalViewers: Int = 0,
)
