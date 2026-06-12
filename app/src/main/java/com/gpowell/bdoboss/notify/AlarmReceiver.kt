package com.gpowell.bdoboss.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isRefresh = intent.getBooleanExtra(AlarmScheduler.EXTRA_REFRESH, false)

        if (isRefresh) {
            // Refresh alarm — no notification, just re-arm so sparse configs stay covered.
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    runCatching { AlarmScheduler.rearm(context) }
                        .onFailure { Log.w(TAG, "rearm failed", it) }
                } finally {
                    pending.finish()
                }
            }
            return
        }

        val boss = intent.getStringExtra(AlarmScheduler.EXTRA_BOSS) ?: return
        val lead = intent.getIntExtra(AlarmScheduler.EXTRA_LEAD, 0)
        val spawnEpoch = intent.getLongExtra(AlarmScheduler.EXTRA_SPAWN_EPOCH, 0L)
        val minutesLeft = if (spawnEpoch > 0) ((spawnEpoch - System.currentTimeMillis() / 1000) / 60).toInt() else lead
        if (spawnEpoch > 0 && minutesLeft < -5) {
            // spawn long past (heavily delayed alarm) — skip notification
        } else {
            NotificationHelper.showBossReminder(context, boss, minutesLeft)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching { AlarmScheduler.rearm(context) }
                    .onFailure { Log.w(TAG, "rearm failed", it) }
            } finally {
                pending.finish()
            }
        }
    }
}
