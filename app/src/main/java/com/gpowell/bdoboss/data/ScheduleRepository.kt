package com.gpowell.bdoboss.data

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.serialization.json.Json
import java.io.File

class ScheduleRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "schedule_override.json")

    @WorkerThread
    fun load(): Schedule {
        val bundled = json.decodeFromString(
            Schedule.serializer(),
            context.assets.open("schedule_na.json").bufferedReader().use { it.readText() },
        )
        val override = runCatching {
            json.decodeFromString(Schedule.serializer(), cacheFile.readText())
        }.getOrNull()
        return if (override != null && override.version > bundled.version) override else bundled
    }

    @WorkerThread
    fun saveOverride(raw: String): Boolean = runCatching {
        json.decodeFromString(Schedule.serializer(), raw) // validate before persisting
        cacheFile.writeText(raw)
    }.isSuccess
}
