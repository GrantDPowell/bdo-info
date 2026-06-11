package com.gpowell.bdoboss.data

import kotlinx.serialization.Serializable

@Serializable
data class SpawnSlot(
    val day: String,        // java.time.DayOfWeek name, e.g. "MONDAY"
    val time: String,       // "HH:mm" in schedule timezone
    val bosses: List<String>,
)

@Serializable
data class Schedule(
    val region: String,
    val timezone: String,
    val version: Int,
    val bosses: List<String>,
    val slots: List<SpawnSlot>,
)
