package com.gpowell.bdoboss.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCharacter(
    val name: String = "",
    @SerialName("class") val className: String = "",
    val level: Int = 0,
    @SerialName("is_main") val isMain: Boolean = false,
)

@Serializable
data class PlayerProfile(
    @SerialName("family_name") val familyName: String = "",
    val region: String = "",
    val guild: String = "",
    @SerialName("gear_score") val gearScore: Int = 0,
    @SerialName("contribution_points") val contributionPoints: Int = 0,
    val energy: Int = 0,
    val characters: List<PlayerCharacter> = emptyList(),
    @SerialName("life_skills") val lifeSkills: Map<String, String> = emptyMap(),
)

@Serializable
data class Coupon(
    val code: String = "",
    val rewards: String = "",
    val platform: String = "",
    val expires: String = "",
)

/**
 * CouponsResponse wraps a list of [Coupon]s with a total count.
 *
 * The BDO Alerts API does not have a guaranteed field name for the coupon array.
 * Observed possibilities: "coupons", "data", "items". We model the two most likely keys
 * ("coupons" and "data") both with defaults of emptyList(). The client's [BdoAlertsApi.coupons]
 * method picks the non-empty list from whichever field was populated, falling back to the
 * other if needed — so any single-field variant deserializes correctly without custom serializers.
 */
@Serializable
data class CouponsResponse(
    val coupons: List<Coupon> = emptyList(),
    // Some API revisions may return the array under "data"
    val data: List<Coupon> = emptyList(),
    val total: Int = 0,
)
