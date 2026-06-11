package com.gpowell.bdoboss.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boss = intent.getStringExtra(AlarmScheduler.EXTRA_BOSS) ?: return
        val lead = intent.getIntExtra(AlarmScheduler.EXTRA_LEAD, 0)
        NotificationHelper.showBossReminder(context, boss, lead)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { AlarmScheduler.rearm(context) } finally { pending.finish() }
        }
    }
}
