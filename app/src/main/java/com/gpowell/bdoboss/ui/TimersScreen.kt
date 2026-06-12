package com.gpowell.bdoboss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val bossColors = mapOf(
    "Kzarka" to Color(0xFF8E44AD), "Kutum" to Color(0xFFC0392B),
    "Nouver" to Color(0xFFE67E22), "Karanda" to Color(0xFF16A085),
    "Garmoth" to Color(0xFFE74C3C), "Offin" to Color(0xFF5DADE2),
    "Vell" to Color(0xFF2E86C1), "Uturi" to Color(0xFF7DCEA0),
    "Sangoon" to Color(0xFFF4D03F), "Bulgasal" to Color(0xFF9B59B6),
    "Quint" to Color(0xFF95A5A6), "Muraka" to Color(0xFF7F8C8D),
    "Golden Pig King" to Color(0xFFF1C40F),
)

@Composable
fun TimersScreen(
    spawns: List<Spawn>,
    onSpawnClick: (Spawn) -> Unit = {},
    headerContent: (@Composable () -> Unit)? = null,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE h:mm a") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        headerContent?.let { header -> item { header() } }
        items(spawns.filter { it.at > now }, key = { it.at.epochSecond }) { spawn ->
            SpawnCard(spawn, now, fmt, onSpawnClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpawnCard(
    spawn: Spawn,
    now: Instant,
    fmt: DateTimeFormatter,
    onSpawnClick: (Spawn) -> Unit,
) {
    val remaining = Duration.between(now, spawn.at)
    val local = spawn.at.atZone(ZoneId.systemDefault())
    Card(
        onClick = { onSpawnClick(spawn) },
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    spawn.bosses.forEach { boss -> BossIcon(boss) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    spawn.bosses.joinToString("  +  "),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    fmt.format(local), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatCountdown(remaining), color = BdoGold,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatCountdown(d: Duration): String {
    val total = d.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
}
