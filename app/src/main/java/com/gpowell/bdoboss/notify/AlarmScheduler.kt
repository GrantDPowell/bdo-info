package com.gpowell.bdoboss.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gpowell.bdoboss.data.LiveSpawnCache
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.domain.ReminderExpander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object AlarmScheduler {
    const val EXTRA_BOSS = "boss"
    const val EXTRA_LEAD = "lead"
    const val EXTRA_SPAWN_EPOCH = "spawn_epoch"
    const val EXTRA_REFRESH = "refresh"
    // Wide enough to cover sparse bosses' next live spawn (e.g. Vell ~3.5 days out).
    private const val WINDOW_HOURS = 120L
    private val rearmMutex = Mutex()

    /** Cancels previously armed alarms and arms the next ~48h of reminders. Safe to call repeatedly. */
    suspend fun rearm(context: Context) = rearmMutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val alarmMgr = app.getSystemService(AlarmManager::class.java)
            val codesFile = File(app.filesDir, "armed_codes.txt")

            // cancel previously armed alarms
            if (codesFile.exists()) {
                codesFile.readLines().mapNotNull { it.toIntOrNull() }.forEach { code ->
                    existingPending(app, code)?.let(alarmMgr::cancel)
                }
            }

            val now = Instant.now()
            val settings = SettingsRepository(app).current()
            // Source of truth = the last-known LIVE spawns (no static/recurring schedule).
            val spawns = LiveSpawnCache.load(app)
                .filter { it.at > now && Duration.between(now, it.at).toHours() <= WINDOW_HOURS }
                .sortedBy { it.at }
            val reminders = ReminderExpander.expand(spawns, settings, now, ZoneId.systemDefault())

            val canExact = if (Build.VERSION.SDK_INT < 31) true else alarmMgr.canScheduleExactAlarms()
            val codes = mutableListOf<Int>()
            for (r in reminders) {
                val code = "${r.boss}|${r.spawnAt.epochSecond}|${r.leadMin}".hashCode()
                val intent = Intent(app, AlarmReceiver::class.java)
                    .putExtra(EXTRA_BOSS, r.boss)
                    .putExtra(EXTRA_LEAD, r.leadMin)
                    .putExtra(EXTRA_SPAWN_EPOCH, r.spawnAt.epochSecond)
                val pi = PendingIntent.getBroadcast(
                    app, code, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val triggerMs = r.triggerAt.toEpochMilli()
                if (canExact) {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                } else {
                    alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 10 * 60_000L, pi)
                }
                codes += code
            }

            // Arm a 24h refresh alarm so sparse configs (e.g. Vell with 93h gaps) never starve.
            val refreshCode = "refresh".hashCode()
            val refreshIntent = Intent(app, AlarmReceiver::class.java)
                .putExtra(EXTRA_REFRESH, true)
            val refreshPi = PendingIntent.getBroadcast(
                app, refreshCode, refreshIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val refreshTriggerMs = now.plusSeconds(24 * 3600).toEpochMilli()
            if (canExact) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, refreshTriggerMs, refreshPi)
            } else {
                alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, refreshTriggerMs, 60 * 60_000L, refreshPi)
            }
            codes += refreshCode

            codesFile.writeText(codes.joinToString("\n"))
        }
    }

    private fun existingPending(context: Context, code: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
}
