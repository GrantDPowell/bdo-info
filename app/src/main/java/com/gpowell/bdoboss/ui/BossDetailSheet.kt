package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.data.BossInfo
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.data.market.MarketRepository
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoGold
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossDetailSheet(
    spawn: Spawn,
    bossInfo: BossInfo?,
    market: MarketRepository,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE h:mm a") }
    val localTime = fmt.format(spawn.at.atZone(ZoneId.systemDefault()))

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    // Live market prices for the drops, one batched call. Chips are simply
    // absent until data arrives; on Offline/HttpError nothing renders.
    var marketPrices by remember { mutableStateOf<Map<Int, MarketPrice>>(emptyMap()) }
    LaunchedEffect(spawn) {
        val ids = spawn.bosses
            .mapNotNull { bossInfo?.forBoss(it) }
            .flatMap { it.drops }
            .map { it.itemId }
            .filter { it > 0 }
            .distinct()
        if (ids.isEmpty()) return@LaunchedEffect
        when (val result = market.prices(ids)) {
            is ApiResult.Success -> marketPrices = result.data.associateBy { it.itemId }
            else -> Unit // no chip — never block or error the sheet
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            var revealIndex = 0
            spawn.bosses.forEachIndexed { index, boss ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                // Boss header row: icon + name + spawn time
                Reveal(index = revealIndex++, shown = shown) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BossIcon(boss, size = 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                boss,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                localTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val entry = bossInfo?.forBoss(boss)
                if (entry == null) {
                    Reveal(index = revealIndex++, shown = shown) {
                        Text(
                            "No drop data available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    entry.drops.forEach { drop ->
                        Reveal(index = revealIndex++, shown = shown) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ItemIcon(drop.icon)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        drop.item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (drop.note.isNotBlank()) {
                                        Text(
                                            drop.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    // Unlisted items come back from arsha as
                                    // all-zero rows — only chip real listings.
                                    val price = marketPrices[drop.itemId]
                                    if (drop.itemId > 0 && price != null && price.basePrice > 0) {
                                        Text(
                                            "💰 ${formatSilver(price.basePrice)} · ${price.stock} in stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BdoGold,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                Text(
                                    drop.chance.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // Disclaimer at the bottom
            if (bossInfo != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    bossInfo.disclaimer,
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Staggered fade+slide reveal used for sheet rows. */
@Composable
private fun Reveal(index: Int, shown: Boolean, content: @Composable () -> Unit) {
    val delayMs = index * 35
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(250, delayMillis = delayMs)) +
            slideInVertically(tween(250, delayMillis = delayMs)) { it / 4 },
    ) {
        content()
    }
}
