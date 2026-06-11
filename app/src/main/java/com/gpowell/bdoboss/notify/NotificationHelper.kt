package com.gpowell.bdoboss.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gpowell.bdoboss.MainActivity
import com.gpowell.bdoboss.R

object NotificationHelper {
    private const val CHANNEL_ID = "boss_spawns"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Boss spawns", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "World boss spawn reminders"
                enableVibration(true)
            },
        )
    }

    fun showBossReminder(context: Context, boss: String, minutesUntil: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val tap = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (minutesUntil <= 0) "$boss is spawning now!"
                   else "$boss spawns in $minutesUntil min"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("World Boss")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context)
            .notify((boss + minutesUntil).hashCode(), notification)
    }
}
