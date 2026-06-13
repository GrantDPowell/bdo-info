package com.gpowell.bdoboss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.goldGlow

@Composable
fun EventsScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Events & Coupons",
            blurb = "Live patch notes, events, and active coupon codes from the BDO Alerts service.",
            bullets = listOf("Current & upcoming events", "Redeemable coupon codes", "Maintenance & patch alerts"),
            onOpenSettings = onOpenSettings,
        )
    } else {
        KeyedPlaceholder("✓ Key saved — Events arrive in the next build")
    }
}

/** The Vitrine "locked tab" state: glowing padlock in a diamond frame + what-you'll-get list. */
@Composable
internal fun LockedFeature(
    title: String,
    blurb: String,
    bullets: List<String>,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // diamond-framed padlock
        Box(
            Modifier
                .size(64.dp)
                .goldGlow(RoundedCornerShape(16.dp), 14.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(16.dp))
                .background(BdoColors.surface1)
                .border(1.dp, BdoColors.goldLine, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = BdoColors.goldHi,
                modifier = Modifier.size(26.dp).rotate(-45f),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = BdoType.display.copy(fontSize = 24.sp), color = BdoColors.onBg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = BdoColors.onFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bullets.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Diamond(size = 6.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(b, style = MaterialTheme.typography.bodyMedium, color = BdoColors.onMuted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = BdoColors.gold, contentColor = BdoColors.onGold),
        ) { Text("Unlock with API key", fontWeight = FontWeight.SemiBold) }
    }
}

/** Centered confirmation shown on keyed tabs whose content ships in a later build. */
@Composable
internal fun KeyedPlaceholder(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = BdoColors.goldHi,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
