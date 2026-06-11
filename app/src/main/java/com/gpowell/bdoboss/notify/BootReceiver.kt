package com.gpowell.bdoboss.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
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
