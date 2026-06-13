package com.gpowell.bdoboss.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.data.DataSource
import com.gpowell.bdoboss.data.DataSources
import com.gpowell.bdoboss.data.SourceStatus
import com.gpowell.bdoboss.ui.theme.BdoColors
import kotlinx.coroutines.launch

/**
 * Credits + live data-source status. Split out of Settings so the Hub can offer
 * Settings and Credits as two distinct destinations.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val sourceStatus = remember { mutableStateMapOf<String, SourceStatus>() }
    LaunchedEffect(Unit) {
        DataSources.all.forEach { src ->
            sourceStatus[src.name] = SourceStatus.CHECKING
            launch { sourceStatus[src.name] = DataSources.status(src) }
        }
    }

    OverlayScaffold(title = "Credits", onBack = onBack) {
        SettingsSectionHeader("MADE WITH")
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(8.dp)) {
                CreditRow("Boss timers & resets", "bdoalerts.net — by LoadingMagic")
                CreditRow("Market prices & history", "arsha.io · BlackDesertMarket.com")
                CreditRow("Item icons & database", "BDO Codex (bdocodex.com)")
                CreditRow("Boss portraits", "mmotimer.com")
                CreditRow("Category names", "Veliainn market resources")
                CreditRow("Fonts", "Geist & Geist Mono, Marcellus (SIL OFL)")
                CreditRow("Game data & art", "© Pearl Abyss Corp.")
                Text(
                    "Built by Grant Powell (OkimaSha). BDO Info is an unofficial fan app, " +
                        "not affiliated with Pearl Abyss.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSectionHeader("DATA SOURCES")
        Text(
            "The APIs and data BDO Info is built on, with live status. Tap any source to visit it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DataSources.all.forEach { src ->
            DataSourceCard(source = src, status = sourceStatus[src.name] ?: SourceStatus.CHECKING)
        }
        Spacer(Modifier.width(0.dp))
    }
}

@Composable
private fun CreditRow(what: String, who: String) {
    Column {
        Text(what, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataSourceCard(source: DataSource, status: SourceStatus) {
    val ctx = LocalContext.current
    Card(
        onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.siteUrl))) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                StatusPill(status)
            }
            Text(source.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(source.attribution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(status: SourceStatus) {
    val checkingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val (label, color) = when (status) {
        SourceStatus.UP -> "Online" to BdoColors.up
        SourceStatus.DOWN -> "Offline" to BdoColors.down
        SourceStatus.BUNDLED -> "Bundled" to BdoColors.gold
        SourceStatus.CHECKING -> "Checking…" to checkingColor
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}
