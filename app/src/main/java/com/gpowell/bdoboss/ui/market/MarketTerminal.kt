package com.gpowell.bdoboss.ui.market

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.ui.formatSilver
import com.gpowell.bdoboss.ui.theme.AreaSparkline
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.LocalEffectsEnabled
import com.gpowell.bdoboss.ui.theme.MarcellusDisplay
import com.gpowell.bdoboss.ui.theme.MiniSparkline
import com.gpowell.bdoboss.ui.theme.SparkBurst
import com.gpowell.bdoboss.ui.theme.sparkSeries

/** One watchlist entry decorated with its live price + derived trend. */
internal data class TerminalItem(
    val fav: Favorite,
    val price: MarketPrice?,
    val grade: Int,
) {
    val basePrice: Long get() = price?.basePrice ?: 0L
    val spread: Double
        get() = price?.let {
            if (it.basePrice > 0 && it.lastSoldPrice > 0) {
                (it.lastSoldPrice - it.basePrice).toDouble() / it.basePrice * 100.0
            } else 0.0
        } ?: 0.0
    val hit: Boolean get() = fav.targetPrice > 0 && basePrice in 1..fav.targetPrice
}

/** ▲/▼ percentage delta. */
@Composable
internal fun Delta(pct: Double, size: Int = 12) {
    val up = pct >= 0
    Text(
        (if (up) "▲" else "▼") + "%.1f%%".format(kotlin.math.abs(pct)),
        style = BdoType.numS.copy(fontSize = size.sp),
        color = if (up) BdoColors.up else BdoColors.down,
    )
}

// ── Trade tape (scrolling marquee of watchlist movers) ──────────────────────────
@Composable
internal fun TradeTape(items: List<TerminalItem>) {
    val priced = items.filter { it.basePrice > 0 }
    if (priced.size < 2) return
    var runW by remember { mutableIntStateOf(0) }
    val t = rememberInfiniteTransition(label = "tape")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(priced.size * 2600, easing = LinearEasing)), label = "phase",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(BdoColors.gold.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = BdoColors.line,
                shape = RoundedCornerShape(0.dp),
            )
            .clip(RoundedCornerShape(0.dp)),
    ) {
        Row(
            Modifier
                .graphicsLayer { translationX = -phase * runW }
                .padding(vertical = 9.dp),
        ) {
            TapeRun(priced, onWidth = { runW = it })
            TapeRun(priced, onWidth = {})
        }
    }
}

@Composable
private fun TapeRun(items: List<TerminalItem>, onWidth: (Int) -> Unit) {
    Row(
        Modifier.onSizeChanged { onWidth(it.width) },
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        items.forEach { it2 ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(it2.fav.title, style = MaterialTheme.typography.labelLarge, color = BdoColors.onMuted, maxLines = 1)
                Delta(it2.spread, size = 11)
            }
            Spacer(Modifier.width(26.dp))
        }
    }
}

// ── Spotlight (biggest mover) ───────────────────────────────────────────────────
@Composable
internal fun Spotlight(item: TerminalItem, onOpen: () -> Unit) {
    val up = item.spread >= 0
    val series = remember(item.fav.itemId, up) { sparkSeries(item.fav.itemId, 60, up) }
    BdoCard(
        facet = true,
        glow = true,
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.Top) {
            ItemIcon(itemId = item.fav.itemId, name = item.fav.title, grade = item.grade, size = 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Diamond(size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("SPOTLIGHT · BIGGEST MOVER", style = BdoType.overline.copy(fontSize = 8.5.sp), color = BdoColors.gold)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    item.fav.title,
                    style = BdoType.display.copy(fontSize = 21.sp),
                    color = BdoColors.onBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (item.basePrice > 0) formatSilver(item.basePrice) else "—",
                    style = BdoType.num.copy(fontSize = 18.sp),
                    color = BdoColors.goldHi,
                )
                Spacer(Modifier.height(4.dp))
                Delta(item.spread, size = 13)
            }
        }
        Spacer(Modifier.height(8.dp))
        AreaSparkline(series, up, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SpotStat("Stock", item.price?.stock?.toString() ?: "—")
            SpotStat("Volume", item.price?.let { formatCompact(it.totalTrades) } ?: "—")
            SpotStat("Floor", item.price?.priceMin?.takeIf { it > 0 }?.let { formatSilver(it) } ?: "—")
            SpotStat("Ceil", item.price?.priceMax?.takeIf { it > 0 }?.let { formatSilver(it) } ?: "—")
        }
    }
}

@Composable
private fun SpotStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = BdoType.numS.copy(fontSize = 12.sp), color = BdoColors.onMuted)
    }
}

// ── Dense watchlist row ─────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SparkRow(
    item: TerminalItem,
    onOpen: () -> Unit,
    onLong: () -> Unit,
    isLast: Boolean,
) {
    val fx = LocalEffectsEnabled.current
    val up = item.spread >= 0
    val series = remember(item.fav.itemId, up) { sparkSeries(item.fav.itemId, 22, up) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (item.hit) BdoColors.up.copy(alpha = 0.07f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ItemIcon(itemId = item.fav.itemId, name = item.fav.title, grade = item.grade, size = 34.dp)
            if (item.hit && fx) SparkBurst(Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.fav.title,
                style = BdoType.display.copy(fontSize = 15.sp),
                color = BdoColors.onBg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    item.fav.targetPrice > 0 -> "🎯 ${formatSilver(item.fav.targetPrice)}${if (item.hit) " · HIT" else ""}"
                    item.price != null -> "Vol ${formatCompact(item.price.totalTrades)}"
                    else -> "—"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.hit) BdoColors.up else BdoColors.onFaint,
            )
        }
        MiniSparkline(series, up, Modifier.size(width = 60.dp, height = 24.dp))
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(76.dp)) {
            Text(
                if (item.basePrice > 0) formatSilver(item.basePrice) else "—",
                style = BdoType.num.copy(fontSize = 14.sp),
                color = if (item.hit) BdoColors.up else BdoColors.onBg,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Delta(item.spread, size = 11)
        }
    }
    if (!isLast) Box(Modifier.fillMaxWidth().height(1.dp).background(BdoColors.line))
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

// ── Category chip rail ──────────────────────────────────────────────────────────
@Composable
internal fun CategoryRail(
    categories: List<com.gpowell.bdoboss.data.market.MarketCategory>,
    onPick: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        categories.forEach { c ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BdoColors.surface1)
                    .border(1.dp, BdoColors.line, RoundedCornerShape(10.dp))
                    .clickableRow { onPick(c.code) }
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(c.name.take(2).uppercase(), style = BdoType.numS.copy(fontSize = 10.sp), color = BdoColors.gold)
                Text(c.name, style = MaterialTheme.typography.labelLarge, color = BdoColors.onMuted, maxLines = 1)
                Text("${c.subs.size}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
            }
        }
    }
}
