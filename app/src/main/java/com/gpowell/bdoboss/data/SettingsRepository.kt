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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class QuietRule(
    val id: Long,                       // stable identity for UI lists
    val label: String = "Quiet hours",
    val enabled: Boolean = true,
    val days: Set<String> = ALL_DAYS,   // DayOfWeek names; the day the window STARTS
    val startMin: Int = 23 * 60,
    val endMin: Int = 8 * 60,
) {
    companion object {
        val ALL_DAYS: Set<String> = java.time.DayOfWeek.entries.map { it.name }.toSet()
    }
}

data class NotificationSettings(
    val masterEnabled: Boolean = true,
    val enabledBosses: Set<String> = ALL_DEFAULT_ON,
    val leadsByBoss: Map<String, Set<Int>> = emptyMap(),
    val quietRules: List<QuietRule> = emptyList(),
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
        private val rulesSerializer = ListSerializer(QuietRule.serializer())

        fun encodeLeads(leads: Map<String, Set<Int>>): String =
            Json.encodeToString(mapSerializer, leads)

        fun decodeLeads(raw: String): Map<String, Set<Int>> =
            runCatching { Json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())

        fun encodeQuietRules(rules: List<QuietRule>): String =
            Json.encodeToString(rulesSerializer, rules)

        fun decodeQuietRules(raw: String): List<QuietRule> =
            runCatching { Json.decodeFromString(rulesSerializer, raw) }.getOrDefault(emptyList())
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val MASTER = booleanPreferencesKey("master_enabled")
        val BOSSES = stringSetPreferencesKey("enabled_bosses")
        val LEADS = stringPreferencesKey("leads_json")
        val QUIET_RULES = stringPreferencesKey("quiet_rules_json")

        // Legacy single-window quiet keys. Left in place (read for migration, never
        // deleted) so a rollback APK keeps working off the old values.
        val QUIET = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start_min")
        val QUIET_END = intPreferencesKey("quiet_end_min")

        val API_KEY = stringPreferencesKey("bdoalerts_api_key")
    }

    // -------------------------------------------------------------------------
    // BDO Alerts API key — stored in DataStore, never committed to the repo.
    // Wire keyProvider = settingsRepository::apiKey into BdoAlertsApi.
    // -------------------------------------------------------------------------

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY] ?: "" }

    suspend fun apiKey(): String = apiKeyFlow.first()

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value }
    }

    val settings: Flow<NotificationSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): NotificationSettings = settings.first()

    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) {
        context.dataStore.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.MASTER] = next.masterEnabled
            p[Keys.BOSSES] = next.enabledBosses
            p[Keys.LEADS] = NotificationSettings.encodeLeads(next.leadsByBoss)
            p[Keys.QUIET_RULES] = NotificationSettings.encodeQuietRules(next.quietRules)
        }
    }

    private fun Preferences.toSettings() = NotificationSettings(
        masterEnabled = this[Keys.MASTER] ?: true,
        enabledBosses = this[Keys.BOSSES] ?: NotificationSettings.ALL_DEFAULT_ON,
        leadsByBoss = NotificationSettings.decodeLeads(this[Keys.LEADS] ?: ""),
        quietRules = quietRulesOrMigrated(),
    )

    /** New key wins; otherwise synthesize one all-days rule from the legacy single window. */
    private fun Preferences.quietRulesOrMigrated(): List<QuietRule> {
        val raw = this[Keys.QUIET_RULES]
        if (raw != null) return NotificationSettings.decodeQuietRules(raw)
        if (this[Keys.QUIET] == true) {
            return listOf(
                QuietRule(
                    id = 1,
                    label = "Quiet hours",
                    days = QuietRule.ALL_DAYS,
                    startMin = this[Keys.QUIET_START] ?: 23 * 60,
                    endMin = this[Keys.QUIET_END] ?: 8 * 60,
                ),
            )
        }
        return emptyList()
    }
}
