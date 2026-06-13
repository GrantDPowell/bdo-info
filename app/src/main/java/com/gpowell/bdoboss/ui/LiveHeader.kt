package com.gpowell.bdoboss.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.live.LiveState
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType

/**
 * Compact single-line status ribbon: LIVE dot · NA · Daily · Weekly · Pig Cave.
 * Replaces the old boxed 3-column header to give the Dial more room. Scrolls
 * horizontally on narrow screens so nothing clips.
 */
@Composable
fun LiveHeader(live: LiveState) {
    val connected = live.connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // LIVE / LOCAL dot
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (connected) {
                val pulse = rememberInfiniteTransition(label = "liveDot")
                val a by pulse.animateFloat(
                    1f, 0.35f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "a",
                )
                Box(
                    Modifier.size(7.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(BdoColors.live),
                )
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(BdoColors.onFaint))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (connected) "LIVE · NA" else "LOCAL · NA",
                style = BdoType.overline.copy(fontSize = 9.5.sp),
                color = if (connected) BdoColors.live2 else BdoColors.onFaint,
            )
        }

        if (connected) {
            live.dailyResetCountdown?.let { Stat("DAILY", it) }
            live.weeklyResetCountdown?.let { Stat("WEEKLY", it) }
            live.caveOpen?.let { open ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PIG CAVE", style = BdoType.overline.copy(fontSize = 9.sp), color = BdoColors.onFaint)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (open) "OPEN" else "CLOSED",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (open) BdoColors.up else BdoColors.down,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = BdoType.overline.copy(fontSize = 9.sp), color = BdoColors.onFaint)
        Spacer(Modifier.width(6.dp))
        RollingText(
            text = value,
            color = BdoColors.onBg,
            style = BdoType.num.copy(fontSize = 13.sp),
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}
