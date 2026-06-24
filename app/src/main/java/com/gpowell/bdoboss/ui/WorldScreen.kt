package com.gpowell.bdoboss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.data.api.CaveStatsResponse
import com.gpowell.bdoboss.data.api.CaveStatus
import com.gpowell.bdoboss.data.api.ResetTimers
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.SectionLabel
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

private const val REGION_W = "na"

/**
 * "World" Bosses sub-tab — live game resets (daily/weekly/Black Shrine/imperial/trade/
 * barter) and Golden Pig Cave status, from the BDO Alerts REST API. Countdowns tick
 * locally off each reset's next_reset timestamp so they're always current.
 */
@Composable
fun WorldScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "World Status",
            blurb = "Live game resets and Golden Pig Cave status from the BDO Alerts service.",
            bullets = listOf("Daily, weekly & Black Shrine resets", "Imperial, trade & barter timers", "Golden Pig Cave OPEN/CLOSED"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var resets by remember { mutableStateOf<ResetTimers?>(null) }
    var cave by remember { mutableStateOf<CaveStatus?>(null) }
    var caveStats by remember { mutableStateOf<CaveStatsResponse?>(null) }
    var schedule by remember { mutableStateOf<Map<String, List<com.gpowell.bdoboss.data.api.ScheduleSlot>>?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        (api.resetTimers(REGION_W) as? ApiResult.Success)?.let { resets = it.data }
        (api.caveStatus(REGION_W) as? ApiResult.Success)?.let { cave = it.data }
        (api.caveStatsTyped(REGION_W) as? ApiResult.Success)?.let { caveStats = it.data }
        (api.bossSchedule(REGION_W) as? ApiResult.Success)?.let { schedule = it.data }
    }
    LaunchedEffect(Unit) {
        while (true) { nowMs = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(2.dp))

        // ── Golden Pig Cave ──
        SectionLabel("Golden Pig Cave")
        val c = cave
        BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, contentPadding = PaddingValues(18.dp)) {
            if (c == null) {
                Text("Loading…", color = BdoColors.onFaint)
            } else {
                val accent = if (c.isOpen) BdoColors.up else BdoColors.onMuted
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Diamond(size = 9.dp, color = accent, glow = c.isOpen)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (c.isOpen) "OPEN" else "CLOSED",
                        style = BdoType.display.copy(fontSize = 26.sp), color = accent, fontWeight = FontWeight.Bold,
                    )
                }
                caveStats?.stats?.get(REGION_W)?.let { s ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${s.totalOpens} opens · ${s.totalCloses} closes · ${s.totalChanges} changes tracked",
                        style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint,
                    )
                }
            }
        }

        // ── Resets ──
        SectionLabel("Resets", Modifier.padding(top = 6.dp))
        val r = resets
        if (r == null) {
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text("Loading…", color = BdoColors.onFaint)
            }
        } else {
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                r.rows().forEachIndexed { i, (label, entry) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Diamond(size = 5.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(label, Modifier.weight(1f), color = BdoColors.onBg)
                        Text(
                            liveCountdown(entry.nextReset, nowMs).ifBlank { entry.countdown },
                            style = BdoType.num.copy(fontSize = 15.sp), color = BdoColors.goldHi,
                        )
                    }
                }
            }
        }
        // ── Weekly schedule (real grid from the API) ──
        SectionLabel("This week", Modifier.padding(top = 6.dp))
        val sch = schedule
        if (sch == null) {
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Text("Loading…", color = BdoColors.onFaint) }
        } else {
            val today = java.time.LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
            DAY_ORDER.filter { sch.containsKey(it) }.forEach { day ->
                val slots = sch[day].orEmpty().filter { it.bosses.isNotEmpty() }
                if (slots.isEmpty()) return@forEach
                BdoCard(Modifier.fillMaxWidth(), facet = day == today, glow = day == today, contentPadding = PaddingValues(12.dp)) {
                    Text(
                        if (day == today) "$day · today" else day,
                        style = BdoType.overline.copy(fontSize = 10.sp),
                        color = if (day == today) BdoColors.goldHi else BdoColors.gold,
                    )
                    Spacer(Modifier.height(6.dp))
                    slots.forEach { slot ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(slot.time, style = BdoType.num.copy(fontSize = 13.sp), color = BdoColors.onMuted, modifier = Modifier.width(56.dp))
                            Text(slot.bosses.joinToString(", "), color = BdoColors.onBg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private val DAY_ORDER = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** Format the time remaining until [iso] (an ISO-8601 instant w/ offset) as "2d 14h 03m" / "14:03". */
private fun liveCountdown(iso: String, nowMs: Long): String {
    val target = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    val secs = Duration.between(Instant.ofEpochMilli(nowMs), target).seconds
    if (secs <= 0) return "now"
    val d = secs / 86400; val h = (secs % 86400) / 3600; val m = (secs % 3600) / 60; val s = secs % 60
    return when {
        d > 0 -> "%dd %02dh %02dm".format(d, h, m)
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        else -> "%d:%02d".format(m, s)
    }
}
