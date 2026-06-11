package com.gpowell.bdoboss.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Boss timer DTOs ──────────────────────────────────────────────────────────

@Serializable
data class WsTimeUntil(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    @SerialName("total_seconds") val totalSeconds: Int = 0,
)

@Serializable
data class WsBoss(
    @SerialName("boss_name") val bossName: String,
    @SerialName("next_spawn") val nextSpawn: String? = null,
    @SerialName("time_until") val timeUntil: WsTimeUntil? = null,
)

// ─── Reset timer DTOs ─────────────────────────────────────────────────────────

@Serializable
data class WsResetEntry(
    @SerialName("next_reset") val nextReset: String? = null,
    @SerialName("time_until") val timeUntil: WsTimeUntil? = null,
    val countdown: String? = null,
)

@Serializable
data class WsResetTimers(
    @SerialName("daily_reset") val dailyReset: WsResetEntry? = null,
    @SerialName("weekly_reset") val weeklyReset: WsResetEntry? = null,
)

// ─── Cave status DTO ─────────────────────────────────────────────────────────

@Serializable
data class WsCaveStatus(
    // "OPEN" or "CLOSED"
    val status: String? = null,
)

// ─── Aggregate live state exposed to UI ──────────────────────────────────────

data class LiveState(
    val connected: Boolean = false,
    val bosses: List<WsBoss> = emptyList(),
    val dailyResetCountdown: String? = null,
    val weeklyResetCountdown: String? = null,
    val caveOpen: Boolean? = null,
)
