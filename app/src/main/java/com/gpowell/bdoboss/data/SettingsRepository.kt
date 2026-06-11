package com.gpowell.bdoboss.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class NotificationSettings(
    val masterEnabled: Boolean = true,
    val enabledBosses: Set<String> = ALL_DEFAULT_ON,
    val leadsByBoss: Map<String, Set<Int>> = emptyMap(),
    val quietEnabled: Boolean = false,
    val quietStartMin: Int = 23 * 60,   // 23:00
    val quietEndMin: Int = 8 * 60,      // 08:00
) {
    fun leadsFor(boss: String): Set<Int> = leadsByBoss[boss] ?: setOf(DEFAULT_LEAD_MIN)

    companion object {
        const val DEFAULT_LEAD_MIN = 15
        val ALL_DEFAULT_ON = setOf(
            "Kzarka", "Kutum", "Nouver", "Karanda", "Garmoth", "Offin", "Vell",
            "Uturi", "Sangoon", "Bulgasal", "Quint", "Muraka", "Golden Pig King",
        )
        private val mapSerializer =
            MapSerializer(String.serializer(), SetSerializer(Int.serializer()))

        fun encodeLeads(leads: Map<String, Set<Int>>): String =
            Json.encodeToString(mapSerializer, leads)

        fun decodeLeads(raw: String): Map<String, Set<Int>> =
            runCatching { Json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val MASTER = booleanPreferencesKey("master_enabled")
        val BOSSES = stringSetPreferencesKey("enabled_bosses")
        val LEADS = stringPreferencesKey("leads_json")
        val QUIET = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_min")
        val QUIET_END = intPreferencesKey("quiet_end_min")
        @Suppress("unused")
        val API_KEY = stringPreferencesKey("bdoalerts_api_key")
    }

    val settings: Flow<NotificationSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): NotificationSettings = settings.first()

    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) {
        context.dataStore.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.MASTER] = next.masterEnabled
            p[Keys.BOSSES] = next.enabledBosses
            p[Keys.LEADS] = NotificationSettings.encodeLeads(next.leadsByBoss)
            p[Keys.QUIET] = next.quietEnabled
            p[Keys.QUIET_START] = next.quietStartMin
            p[Keys.QUIET_END] = next.quietEndMin
        }
    }

    private fun Preferences.toSettings() = NotificationSettings(
        masterEnabled = this[Keys.MASTER] ?: true,
        enabledBosses = this[Keys.BOSSES] ?: NotificationSettings.ALL_DEFAULT_ON,
        leadsByBoss = NotificationSettings.decodeLeads(this[Keys.LEADS] ?: ""),
        quietEnabled = this[Keys.QUIET] ?: false,
        quietStartMin = this[Keys.QUIET_START] ?: 23 * 60,
        quietEndMin = this[Keys.QUIET_END] ?: 8 * 60,
    )
}
