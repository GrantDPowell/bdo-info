package com.gpowell.bdoboss.data

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BossDrop(
    val item: String,
    val chance: String = "",
    val confidence: String = "unknown",
    val note: String = "",
    @SerialName("item_id") val itemId: Int = 0,
    val icon: String = "",
) {
    /** BDO Codex item page for this drop, or null when no concrete item id is known. */
    val codexUrl: String?
        get() = if (itemId > 0) "https://bdocodex.com/us/item/$itemId/" else null
}

@Serializable
data class BossEntry(val name: String, val drops: List<BossDrop>)

@Serializable
data class BossInfo(
    val disclaimer: String,
    val bosses: List<BossEntry>,
    val version: Int = 1,
) {
    fun forBoss(name: String): BossEntry? = bosses.firstOrNull { it.name == name }
}

class BossInfoRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "boss_info_override.json")

    @WorkerThread
    fun load(): BossInfo {
        val bundled = json.decodeFromString(
            BossInfo.serializer(),
            context.assets.open("boss_info.json").bufferedReader().use { it.readText() },
        )
        val override = runCatching {
            json.decodeFromString(BossInfo.serializer(), cacheFile.readText())
        }.getOrNull()
        return if (override != null && override.version > bundled.version) override else bundled
    }

    @WorkerThread
    fun saveOverride(raw: String): Boolean = runCatching {
        json.decodeFromString(BossInfo.serializer(), raw) // validate before persisting
        cacheFile.writeText(raw)
    }.isSuccess
}
