package com.gpowell.bdoboss.data

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BossDrop(
    val item: String,
    val chance: String = "",
    val confidence: String = "unknown",
    val note: String = "",
    val icon: String = "",
)

@Serializable
data class BossEntry(val name: String, val drops: List<BossDrop>)

@Serializable
data class BossInfo(val disclaimer: String, val bosses: List<BossEntry>) {
    fun forBoss(name: String): BossEntry? = bosses.firstOrNull { it.name == name }
}

class BossInfoRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    @WorkerThread
    fun load(): BossInfo =
        json.decodeFromString(
            BossInfo.serializer(),
            context.assets.open("boss_info.json").bufferedReader().use { it.readText() },
        )
}
