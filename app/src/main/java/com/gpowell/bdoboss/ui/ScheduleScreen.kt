package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.MarcellusDisplay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Live agenda: the real upcoming spawns from the bdoalerts feed, grouped by local day.
 * No static weekly grid — everything here is current live data (or the last-known live
 * snapshot when offline), so there's nothing to hand-maintain.
 */
@Composable
fun ScheduleScreen(spawns: List<Spawn>, onSpawnClick: (Spawn) -> Unit = {}) {
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = Instant.now(); kotlinx.coroutines.delay(1000) }
    }

    val upcoming = remember(spawns, now) { spawns.filter { it.at > now }.sortedBy { it.at } }
    val byDay = remember(upcoming, zone) {
        upcoming.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap()
    }
    val today = remember(now, zone) { now.atZone(zone).toLocalDate() }

    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    if (upcoming.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Diamond(size = 10.dp, glow = true)
                Spacer(Modifier.height(10.dp))
                Text("Waiting for the live feed…", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Spawn times come straight from the live boss feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BdoColors.onFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val days = byDay.keys.toList()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        itemsIndexed(days, key = { _, d -> d.toEpochDay() }) { index, date ->
            val delayMs = (index * 55).coerceAtMost(280)
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(420, delayMillis = delayMs)) +
                    slideInVertically(tween(420, delayMillis = delayMs)) { it / 8 },
            ) {
                DayCard(
                    date = date,
                    isToday = date == today,
                    spawns = byDay[date].orEmpty(),
                    now = now,
                    fmt = fmt,
                    zone = zone,
                    onSpawnClick = onSpawnClick,
                )
            }
        }
    }
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
    BdoCard(
        modifier = Modifier.fillMaxWidth(),
        facet = isToday,
        glow = isToday,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            (date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + if (isToday) "  ·  today" else "").uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MarcellusDisplay),
            color = if (isToday) BdoColors.goldHi else BdoColors.onBg,
        )
        Spacer(Modifier.height(8.dp))
        spawns.sortedBy { it.at }.forEachIndexed { i, spawn ->
            val isNext = i == 0 && isToday && spawn == spawns.minByOrNull { it.at }
            val remaining = Duration.between(now, spawn.at)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSpawnClick(spawn) }
                    .padding(vertical = 6.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shortCountdown(remaining),
                    style = BdoType.numS.copy(fontSize = 12.sp),
                    color = BdoColors.onFaint,
                )
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
