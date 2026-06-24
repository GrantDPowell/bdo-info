package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.data.api.CaveStatus
import com.gpowell.bdoboss.data.api.ResetTimers
import com.gpowell.bdoboss.data.api.ScheduleSlot
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.MarcellusDisplay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
private const val REGION_A = "na"

/**
 * Agenda — the merged "when does everything happen" view (formerly Timers + Agenda + World):
 *  • a compact world strip (Golden Pig Cave + daily/weekly reset countdowns),
 *  • a FULL WEEK of boss spawns: the live feed (next ~24–48h, exact) merged over the
 *    authoritative weekly grid from /api/boss-schedule (PA wall-clock), grouped by local
 *    day with live countdowns.
 */
@Composable
fun ScheduleScreen(spawns: List<Spawn>, onSpawnClick: (Spawn) -> Unit = {}) {
    val ctx = LocalContext.current
    val settings = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by settings.apiKeyFlow.collectAsState(initial = "")
    val api = remember { BdoAlertsApi(keyProvider = { settings.apiKey() }) }

    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); kotlinx.coroutines.delay(1000) } }

    var schedule by remember { mutableStateOf<Map<String, List<ScheduleSlot>>?>(null) }
    var resets by remember { mutableStateOf<ResetTimers?>(null) }
    var cave by remember { mutableStateOf<CaveStatus?>(null) }
    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) return@LaunchedEffect
        (api.bossSchedule(REGION_A) as? ApiResult.Success)?.let { schedule = it.data }
        (api.resetTimers(REGION_A) as? ApiResult.Success)?.let { resets = it.data }
        (api.caveStatus(REGION_A) as? ApiResult.Success)?.let { cave = it.data }
    }

    // Live (exact, near-term) merged over the weekly grid (full week), deduped per minute.
    val week = remember(schedule, now) { schedule?.let { scheduleToWeek(it, now) } ?: emptyList() }
    val merged = remember(spawns, week, now) {
        (spawns.filter { it.at.isAfter(now) } + week)
            .distinctBy { it.at.epochSecond / 60 }
            .sortedBy { it.at }
    }
    val byDay = remember(merged, zone) { merged.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap() }
    val today = remember(now, zone) { now.atZone(zone).toLocalDate() }

    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val days = byDay.keys.toList()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        item { WorldStrip(cave, resets, now) }

        if (days.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Diamond(size = 9.dp, glow = true)
                        Spacer(Modifier.height(8.dp))
                        Text("Loading the week…", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                        Text("Spawns come from the live feed + weekly schedule.", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        itemsIndexed(days, key = { _, d -> d.toEpochDay() }) { index, date ->
            val delayMs = (index * 55).coerceAtMost(280)
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(420, delayMillis = delayMs)) +
                    slideInVertically(tween(420, delayMillis = delayMs)) { it / 8 },
            ) {
                DayCard(date, date == today, byDay[date].orEmpty(), now, fmt, zone, onSpawnClick)
            }
        }
    }
}

/** Project the recurring weekly grid into concrete future spawns for the next 7 PA days. */
private fun scheduleToWeek(schedule: Map<String, List<ScheduleSlot>>, now: Instant): List<Spawn> {
    val today = now.atZone(PA_ZONE).toLocalDate()
    val out = mutableListOf<Spawn>()
    for (i in 0..6) {
        val date = today.plusDays(i.toLong())
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        val slots = schedule[dayName] ?: continue
        for (slot in slots) {
            if (slot.bosses.isEmpty()) continue
            val t = runCatching { LocalTime.parse(slot.time) }.getOrNull() ?: continue
            val instant = date.atTime(t).atZone(PA_ZONE).toInstant()
            if (instant.isAfter(now)) out.add(Spawn(instant, slot.bosses))
        }
    }
    return out
}

@Composable
private fun WorldStrip(cave: CaveStatus?, resets: ResetTimers?, now: Instant) {
    BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Golden Pig Cave
            val open = cave?.isOpen == true
            Diamond(size = 7.dp, color = if (open) BdoColors.up else BdoColors.onMuted, glow = open)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Golden Pig Cave", style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
                Text(
                    cave?.let { if (it.isOpen) "OPEN" else "CLOSED" } ?: "…",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = if (open) BdoColors.up else BdoColors.onMuted,
                )
            }
            resets?.daily?.let { ResetChip("Daily", it.nextReset, now) }
            Spacer(Modifier.width(14.dp))
            resets?.weekly?.let { ResetChip("Weekly", it.nextReset, now) }
        }
    }
}

@Composable
private fun ResetChip(label: String, nextReset: String, now: Instant) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
        Text(resetCountdown(nextReset, now), style = BdoType.numS.copy(fontSize = 13.sp), color = BdoColors.goldHi)
    }
}

private fun resetCountdown(iso: String, now: Instant): String {
    val target = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return "—"
    val s = Duration.between(now, target).seconds
    if (s <= 0) return "now"
    val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; else -> "${m}m" }
}

@Composable
private fun DayCard(
    date: LocalDate,
    isToday: Boolean,
    spawns: List<Spawn>,
    now: Instant,
    fmt: DateTimeFormatter,
    zone: ZoneId,
    onSpawnClick: (Spawn) -> Unit,
) {
    BdoCard(modifier = Modifier.fillMaxWidth(), facet = isToday, glow = isToday, contentPadding = PaddingValues(14.dp)) {
        Text(
            (date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + if (isToday) "  ·  today" else "").uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MarcellusDisplay),
            color = if (isToday) BdoColors.goldHi else BdoColors.onBg,
        )
        Spacer(Modifier.height(8.dp))
        spawns.sortedBy { it.at }.forEachIndexed { i, spawn ->
            val isNext = i == 0 && isToday
            val remaining = Duration.between(now, spawn.at)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSpawnClick(spawn) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    fmt.format(spawn.at.atZone(zone)),
                    modifier = Modifier.width(78.dp),
                    style = BdoType.numS.copy(fontSize = 13.sp),
                    color = if (isNext) BdoColors.goldHi else BdoColors.onMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    spawn.bosses.take(3).forEach { boss -> BossTile(boss, size = 26.dp) }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    spawn.bosses.joinToString(" + "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isNext) BdoColors.onBg else BdoColors.onMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(shortCountdown(remaining), style = BdoType.numS.copy(fontSize = 12.sp), color = BdoColors.onFaint)
            }
        }
    }
}

private fun shortCountdown(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "${h}h" else "${m}m"
}
