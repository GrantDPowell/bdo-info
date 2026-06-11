package com.gpowell.bdoboss

import android.app.Application
import com.gpowell.bdoboss.notify.NotificationHelper

class BdoBossApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
