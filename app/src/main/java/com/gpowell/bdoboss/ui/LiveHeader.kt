package com.gpowell.bdoboss.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond

/**
 * The faceted, glowing status header: LIVE dot + region, then three hairline-split columns
 * (Daily reset · Weekly reset · Pig Cave). Falls back to a calm LOCAL state offline.
 */
@Composable
fun LiveHeader(live: LiveState) {
    val connected = live.connected
    val hasData = connected &&
        (live.dailyResetCountdown != null || live.weeklyResetCountdown != null || live.caveOpen != null)

    BdoCard(
        facet = true,
        glow = connected,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (connected) {
                val pulse = rememberInfiniteTransition(label = "liveDot")
                val a by pulse.animateFloat(
                    1f, 0.35f,
                    infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "a",
                )
                val s by pulse.animateFloat(
                    1f, 0.82f,
                    infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "s",
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = a; scaleX = s; scaleY = s }
                        .clip(CircleShape)
                        .background(BdoColors.live),
                )
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(BdoColors.onFaint))
            }
            Spacer(Modifier.width(7.dp))
            Text(
                if (connected) "LIVE" else "LOCAL",
                style = BdoType.overline,
                color = if (connected) BdoColors.live2 else BdoColors.onFaint,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (connected) "NA · synced" else "NA · schedule only",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = BdoColors.onFaint,
            )
        }

        if (hasData) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResetStat("Daily reset", live.dailyResetCountdown ?: "—", Modifier.weight(1f))
                Divider()
                ResetStat("Weekly reset", live.weeklyResetCountdown ?: "—", Modifier.weight(1f))
                Divider()
                Column(Modifier.weight(1f)) {
                    Text("PIG CAVE", style = BdoType.overline.copy(fontSize = 9.5.sp), color = BdoColors.onFaint)
                    Spacer(Modifier.height(6.dp))
                    val open = live.caveOpen == true
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Diamond(size = 6.dp, color = if (open) BdoColors.up else BdoColors.down, glow = open)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (open) "OPEN" else "CLOSED",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = if (open) BdoColors.up else BdoColors.down,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 9.5.sp), color = BdoColors.onFaint)
        Spacer(Modifier.height(6.dp))
        RollingText(
            text = value,
            color = BdoColors.onBg,
            style = BdoType.num.copy(fontSize = 17.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(BdoColors.line))
}
