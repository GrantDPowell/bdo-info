package com.gpowell.bdoboss.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.live.LiveState

private val LiveGreen = Color(0xFF2ECC71)
private val LiveGray  = Color(0xFF7F8C8D)

@Composable
fun LiveHeader(live: LiveState) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Connection status row ─────────────────────────────────────────
            Crossfade(targetState = live.connected, label = "liveState") { connected ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (connected) {
                        val pulse = rememberInfiniteTransition(label = "dotPulse")
                        val dotScale by pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                            label = "dotScale",
                        )
                        val dotAlpha by pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.6f,
                            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                            label = "dotAlpha",
                        )
                        Box(
                            Modifier
                                .size(10.dp)
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                    alpha = dotAlpha
                                }
                                .clip(CircleShape)
                                .background(LiveGreen),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(LiveGray),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (connected) "LIVE" else "LOCAL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (connected) LiveGreen else LiveGray,
                    )
                }
            }

            // ── Reset countdowns + cave (only when connected) ─────────────────
            if (live.connected && (live.dailyResetCountdown != null || live.weeklyResetCountdown != null || live.caveOpen != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    live.dailyResetCountdown?.let { cd ->
                        LabeledValue(label = "Daily", value = cd)
                    }
                    live.weeklyResetCountdown?.let { cd ->
                        LabeledValue(label = "Weekly", value = cd)
                    }
                    live.caveOpen?.let { open ->
                        LabeledValue(
                            label = "Pig Cave",
                            value = if (open) "OPEN" else "CLOSED",
                            valueColor = if (open) LiveGreen else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RollingText(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
