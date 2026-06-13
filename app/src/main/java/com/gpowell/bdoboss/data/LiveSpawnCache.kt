package com.gpowell.bdoboss.data

import android.content.Context
import com.gpowell.bdoboss.domain.Spawn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Persists the latest LIVE world-boss spawns (from the bdoalerts feed) so the app's
 * single source of truth is real, current data — never a hand-maintained schedule.
 *
 * The feed reports each boss's next spawn; we snapshot those here whenever the app is
 * connected. Alarms and the Bosses tab read from this snapshot, so they fire correctly
 * offline and survive reboots, and they refresh automatically every time the app sees
 * the feed. There is intentionally NO recurring-schedule extrapolation — only spawns the
 * live feed actually reported.
 */
object LiveSpawnCache {
    private const val FILE = "live_spawns.json"

    @Serializable
    private data class Entry(val at: Long, val bosses: List<String>)

    @Serializable
    private data class Snapshot(val savedAt: Long = 0, val spawns: List<Entry> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** Persist the current live spawns (epoch-second + co-spawn boss list). */
    fun save(context: Context, spawns: List<Spawn>) {
        val snap = Snapshot(
            savedAt = Instant.now().epochSecond,
            spawns = spawns.map { Entry(it.at.epochSecond, it.bosses) },
        )
        runCatching { file(context).writeText(json.encodeToString(Snapshot.serializer(), snap)) }
    }

    /** Load the last-known live spawns. Empty if never connected. */
    fun load(context: Context): List<Spawn> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(Snapshot.serializer(), f.readText())
                .spawns.map { Spawn(Instant.ofEpochSecond(it.at), it.bosses) }
        }.getOrDefault(emptyList())
    }
}
