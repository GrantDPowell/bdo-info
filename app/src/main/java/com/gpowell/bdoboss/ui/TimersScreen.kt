package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Retained for other screens that still reference per-boss accent colors.
val bossColors = mapOf(
    "Kzarka" to Color(0xFF8E44AD), "Kutum" to Color(0xFFC0392B),
    "Nouver" to Color(0xFFE67E22), "Karanda" to Color(0xFF16A085),
    "Garmoth" to Color(0xFFE74C3C), "Offin" to Color(0xFF5DADE2),
    "Vell" to Color(0xFF2E86C1), "Uturi" to Color(0xFF7DCEA0),
    "Sangoon" to Color(0xFFF4D03F), "Bulgasal" to Color(0xFF9B59B6),
    "Quint" to Color(0xFF95A5A6), "Muraka" to Color(0xFF7F8C8D),
    "Golden Pig King" to Color(0xFFF1C40F),
)

/** Single-line text whose changes roll vertically (odometer style). */
@Composable
internal fun RollingText(
    text: String,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { -it / 2 } + fadeIn(tween(180))) togetherWith
                (slideOutVertically { it / 2 } + fadeOut(tween(120))) using SizeTransform(clip = true)
        },
        label = "roll",
        modifier = modifier,
    ) { t ->
        Text(t, color = color, style = style, fontWeight = fontWeight, maxLines = 1)
    }
}

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

    // Saveable so the staggered entrance plays once and does NOT replay on tab switches.
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val upcoming = spawns.filter { it.at > now }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        headerContent?.let { header -> item { Box(Modifier.padding(bottom = 4.dp)) { header() } } }
        itemsIndexed(upcoming, key = { _, spawn -> spawn.at.epochSecond }) { index, spawn ->
            val delayMs = (index * 55).coerceAtMost(440)
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(420, delayMillis = delayMs)) +
                    slideInVertically(tween(420, delayMillis = delayMs)) { it / 8 },
                modifier = Modifier.animateItem(),
            ) {
                SpawnCard(spawn, now, fmt, isNext = index == 0, onSpawnClick = onSpawnClick)
            }
        }
    }
}

@Composable
private fun SpawnCard(
    spawn: Spawn,
    now: Instant,
    fmt: DateTimeFormatter,
    isNext: Boolean,
    onSpawnClick: (Spawn) -> Unit,
) {
    val remaining = Duration.between(now, spawn.at)
    val imminent = !remaining.isNegative && remaining < Duration.ofMinutes(10)
    val local = spawn.at.atZone(ZoneId.systemDefault())
    val co = spawn.bosses.size > 1

    val countdownColor = when {
        imminent -> BdoColors.live2
        isNext -> BdoColors.goldHi
        else -> BdoColors.onBg
    }

    BdoCard(
        facet = isNext,
        glow = isNext || imminent,
        onClick = { onSpawnClick(spawn) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── stacked portraits (co-spawn) ──
            Box(Modifier.width(if (co) 64.dp else 52.dp).height(52.dp)) {
                spawn.bosses.take(2).forEachIndexed { i, b ->
                    Box(Modifier.offset(x = (i * 18).dp)) {
                        BossTile(b, size = if (co) 44.dp else 52.dp, glow = isNext)
                    }
                }
                if (spawn.bosses.size > 2) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(50))
                            .background(BdoColors.surfaceHi)
                            .border(1.dp, BdoColors.goldLine, RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "+${spawn.bosses.size - 2}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BdoColors.goldHi,
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))

            // ── name + time ──
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNext) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brush.verticalGradient(listOf(BdoColors.goldHi, BdoColors.gold)))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text("NEXT", style = BdoType.overline.copy(fontSize = 9.sp), color = BdoColors.onGold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        spawn.bosses.joinToString(" + "),
                        style = MaterialTheme.typography.titleMedium,
                        color = BdoColors.onBg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmt.format(local),
                        style = MaterialTheme.typography.bodySmall,
                        color = BdoColors.onFaint,
                    )
                    if (co) {
                        Spacer(Modifier.width(6.dp))
                        Diamond(size = 4.dp, color = BdoColors.onFaint)
                        Spacer(Modifier.width(6.dp))
                        Text("Co-spawn", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))

            // ── countdown ──
            Column(horizontalAlignment = Alignment.End) {
                RollingText(
                    text = formatCountdown(remaining),
                    color = countdownColor,
                    style = BdoType.num.copy(fontSize = if (imminent) 22.sp else 18.sp),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (imminent) "SPAWNING" else "UNTIL SPAWN",
                    style = BdoType.overline.copy(fontSize = 8.5.sp),
                    color = BdoColors.onFaint,
                )
            }
        }
    }
}

private fun formatCountdown(d: Duration): String {
    val total = d.seconds
    if (total <= 0) return "LIVE"
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
}
