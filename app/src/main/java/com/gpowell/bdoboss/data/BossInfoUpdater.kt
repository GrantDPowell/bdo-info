package com.gpowell.bdoboss.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class BossInfoUpdater(private val context: Context) {
    private val client = OkHttpClient()

    /** Returns true if a newer boss info file was downloaded and cached. */
    suspend fun checkForUpdate(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        runCatching {
            val repo = BossInfoRepository(context)
            val oldVersion = repo.load().version
            val saved = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body?.string() ?: return@withContext false
                repo.saveOverride(body)
            }
            if (!saved) return@withContext false
            val newVersion = repo.load().version
            newVersion > oldVersion
        }.onFailure { Log.d(TAG, "boss info update check failed: ${it.message}") }
            .getOrDefault(false)
    }

    private companion object {
        const val TAG = "BossInfoUpdater"
    }
}
