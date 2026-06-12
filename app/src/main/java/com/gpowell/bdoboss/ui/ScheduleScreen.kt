package com.gpowell.bdoboss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.data.Schedule
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.domain.SpawnCalculator
import com.gpowell.bdoboss.ui.theme.BdoGold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ScheduleScreen(schedule: Schedule, onSpawnClick: (Spawn) -> Unit = {}) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val fmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    // One computation for the whole 7-day window, grouped by local date.
    val spawnsByDate = remember(schedule) {
        SpawnCalculator
            .upcoming(schedule, Instant.now(), count = 200)
            .groupBy { it.at.atZone(zone).toLocalDate() }
    }
    val nextSpawnAt = remember(schedule) {
        SpawnCalculator.upcoming(schedule, Instant.now(), count = 1).firstOrNull()?.at
    }
    val days = remember { (0..6L).map { today.plusDays(it) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(days, key = { it.toEpochDay() }) { date ->
            DayCard(
                date = date,
                isToday = date == today,
                spawns = spawnsByDate[date].orEmpty(),
                nextSpawnAt = nextSpawnAt,
                fmt = fmt,
                zone = zone,
                onSpawnClick = onSpawnClick,
            )
        }
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    isToday: Boolean,
    spawns: List<Spawn>,
    nextSpawnAt: Instant?,
    fmt: DateTimeFormatter,
    zone: ZoneId,
    onSpawnClick: (Spawn) -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) +
                    if (isToday) "  ·  today" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isToday) BdoGold else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            if (spawns.isEmpty()) {
                Text(
                    "No more spawns today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            spawns.forEach { spawn ->
                val isNext = spawn.at == nextSpawnAt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpawnClick(spawn) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        fmt.format(spawn.at.atZone(zone)),
                        modifier = Modifier.width(86.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNext) BdoGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        spawn.bosses.forEach { boss -> BossIcon(boss, size = 20.dp) }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        spawn.bosses.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNext) BdoGold else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
