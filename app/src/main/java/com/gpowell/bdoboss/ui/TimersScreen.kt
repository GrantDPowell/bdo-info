package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
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

private val UrgentRed = Color(0xFFFF6B5A)

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
        Text(t, color = color, style = style, fontWeight = fontWeight)
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

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val upcoming = spawns.filter { it.at > now }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        headerContent?.let { header -> item { header() } }
        itemsIndexed(upcoming, key = { _, spawn -> spawn.at.epochSecond }) { index, spawn ->
            val delayMs = (index * 40).coerceAtMost(400)
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(300, delayMillis = delayMs)) +
                    slideInVertically(tween(300, delayMillis = delayMs)) { it / 6 },
                modifier = Modifier.animateItem(),
            ) {
                SpawnCard(spawn, now, fmt, isNext = index == 0, onSpawnClick = onSpawnClick)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpawnCard(
    spawn: Spawn,
    now: Instant,
    fmt: DateTimeFormatter,
    isNext: Boolean,
    onSpawnClick: (Spawn) -> Unit,
) {
    val remaining = Duration.between(now, spawn.at)
    val local = spawn.at.atZone(ZoneId.systemDefault())
    val shape = RoundedCornerShape(14.dp)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )

    val countdownColor by animateColorAsState(
        targetValue = if (remaining < Duration.ofMinutes(15)) UrgentRed else BdoGold,
        animationSpec = tween(600),
        label = "urgency",
    )

    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        val cardModifier = if (isNext) {
            val pulse by rememberInfiniteTransition(label = "heroPulse").animateFloat(
                initialValue = 0.35f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                label = "pulse",
            )
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, BdoGold.copy(alpha = pulse), shape)
        } else {
            Modifier.fillMaxWidth()
        }
        Card(
            onClick = { onSpawnClick(spawn) },
            shape = shape,
            interactionSource = interaction,
            modifier = cardModifier,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        spawn.bosses.forEach { boss -> BossIcon(boss, size = 44.dp) }
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
                RollingText(
                    text = formatCountdown(remaining),
                    color = countdownColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (isNext) {
            // Plain Box, not Surface — Surface blocks touch input, creating a dead
            // zone over the card's tap target.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .background(BdoGold, RoundedCornerShape(6.dp)),
            ) {
                Text(
                    "NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
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
