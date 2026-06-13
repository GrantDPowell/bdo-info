package com.gpowell.bdoboss.ui.market

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.market.IndexedItem
import com.gpowell.bdoboss.data.market.ItemIndexRepository
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.data.market.MarketRepository
import com.gpowell.bdoboss.data.market.PricePoint
import com.gpowell.bdoboss.ui.formatSilver
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoGold
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.goldGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Item detail: in-depth stats panel + enhancement-level price table (itemAll)
// + history chart (history) with range + enhancement-level toggles + ⭐ watchlist.
// ---------------------------------------------------------------------------

/**
 * Label for an arsha `sid` (enhancement level). Accessories run sid 0..5 and
 * use PRI..PEN names from sid 1; gear runs sid 0..20 where 1..15 are "+N" and
 * 16..20 are PRI..PEN. [maxSid] (highest sid the item actually has) tells the
 * two apart. Pure and unit-tested.
 */
internal fun enhancementLabel(sid: Int, maxSid: Int): String = when {
    sid == 0 -> "Base"
    sid in 16..20 -> PEN_NAMES[sid - 16]
    sid in 1..5 && maxSid <= 5 -> PEN_NAMES[sid - 1]
    else -> "+$sid"
}

private val PEN_NAMES = listOf("PRI", "DUO", "TRI", "TET", "PEN")

/** Days kept per history-range option. */
private enum class HistoryRange(val label: String, val days: Int) {
    D7("7D", 7), D30("30D", 30), D90("90D", 90)
}

/** Coarse "x ago" for a last-sold epoch-second timestamp. Pure + unit-tested. */
internal fun relativeTime(epochSec: Long, nowSec: Long): String {
    if (epochSec <= 0) return "—"
    val s = (nowSec - epochSec).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        s < 2_592_000 -> "${s / 86_400}d ago"
        else -> "${s / 2_592_000}mo ago"
    }
}

@Composable
fun ItemDetailScreen(
    itemId: Int,
    market: MarketRepository,
    favoritesRepo: FavoritesRepository,
    itemIndex: ItemIndexRepository,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val codexUrl = "https://bdocodex.com/us/item/$itemId/"

    val favorites by favoritesRepo.favorites.collectAsState(initial = emptyList())
    val isFav = remember(favorites, itemId) {
        Favorite.findMatch(favorites, FavoriteType.ITEM, itemId = itemId) != null
    }

    var indexed by remember(itemId) { mutableStateOf<IndexedItem?>(null) }
    var rows by remember(itemId) { mutableStateOf<List<MarketPrice>?>(null) } // null = loading
    var rowsUnavailable by remember(itemId) { mutableStateOf(false) }

    // History is fetched per selected enhancement level.
    var selectedSid by remember(itemId) { mutableStateOf(0) }
    var range by remember(itemId) { mutableStateOf(HistoryRange.D90) }
    var history by remember(itemId) { mutableStateOf<List<PricePoint>?>(null) } // null = loading

    LaunchedEffect(itemId) {
        indexed = withContext(Dispatchers.IO) { itemIndex.byId(itemId) }
        when (val result = market.itemAll(itemId)) {
            is ApiResult.Success ->
                // Unlisted enhancement levels come back as all-zero rows — skip.
                rows = result.data.filter { it.basePrice > 0 || it.stock > 0 || it.lastSoldPrice > 0 }
            else -> {
                rows = emptyList()
                rowsUnavailable = true
            }
        }
    }

    LaunchedEffect(itemId, selectedSid) {
        history = null
        history = when (val result = market.history(itemId, selectedSid)) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    val name = rows?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: indexed?.name
        ?: "Item $itemId"
    val baseRow = rows?.firstOrNull { it.enhancement == 0 } ?: rows?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        // Header: back, monogram, name, star.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BdoGold)
            }
            ItemIcon(itemId = itemId, name = name, grade = indexed?.grade ?: 0, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = {
                scope.launch {
                    favoritesRepo.toggle(
                        FavoriteType.ITEM,
                        title = name,
                        url = codexUrl,
                        itemId = itemId,
                    )
                }
            }) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFav) "Remove from watchlist" else "Add to watchlist",
                    tint = if (isFav) BdoGold else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // In-depth stats from the base (sid 0) row.
        if (baseRow != null && (baseRow.basePrice > 0 || baseRow.totalTrades > 0)) {
            Spacer(Modifier.height(4.dp))
            StatsPanel(baseRow)
        }

        Spacer(Modifier.height(12.dp))
        MarketSectionHeader("MARKET PRICES")
        Spacer(Modifier.height(8.dp))
        val priceRows = rows
        when {
            priceRows == null -> DimLine("Loading prices…")
            rowsUnavailable -> DimLine("Marketplace temporarily unavailable — prices may be missing")
            priceRows.isEmpty() -> DimLine("Not currently listed on the Central Market.")
            else -> PriceTable(priceRows)
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketSectionHeader("PRICE HISTORY")
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HistoryRange.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BdoGold.copy(alpha = 0.22f),
                            selectedLabelColor = BdoGold,
                        ),
                    )
                }
            }
        }

        // Enhancement-level selector when the item enhances (sids beyond base).
        val sids = priceRows?.map { it.enhancement }?.distinct()?.sorted().orEmpty()
        if (sids.size > 1) {
            val maxSid = sids.max()
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sids.forEach { sid ->
                    FilterChip(
                        selected = selectedSid == sid,
                        onClick = { selectedSid = sid },
                        label = { Text(enhancementLabel(sid, maxSid), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BdoGold.copy(alpha = 0.22f),
                            selectedLabelColor = BdoGold,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val allPoints = history
        val points = remember(allPoints, range) { filterRange(allPoints.orEmpty(), range.days) }
        when {
            allPoints == null -> DimLine("Loading history…")
            points.isEmpty() -> DimLine("No history available.")
            else -> PriceHistoryChart(
                points = points,
                modifier = Modifier.fillMaxWidth().height(170.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        // v1: external browser. Future: route into the Hub tab's in-app browser.
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(codexUrl))) }
        }) {
            Text("View on BDO Codex", color = BdoGold)
        }
    }
}

/** Keeps points within [days] of the latest sample (data can lag "now"). */
private fun filterRange(points: List<PricePoint>, days: Int): List<PricePoint> {
    if (points.isEmpty()) return points
    val cutoff = points.last().at - days.toLong() * 86_400L
    val kept = points.filter { it.at >= cutoff }
    return if (kept.size >= 2) kept else points
}

@Composable
private fun DimLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// In-depth stats panel
// ---------------------------------------------------------------------------

@Composable
private fun StatsPanel(row: MarketPrice) {
    val spread = if (row.basePrice > 0 && row.lastSoldPrice > 0) {
        (row.lastSoldPrice - row.basePrice).toDouble() / row.basePrice * 100.0
    } else {
        null
    }
    val now = System.currentTimeMillis() / 1000L
    BdoCard(facet = true, glow = true, modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        // Price hero
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("BASE PRICE", style = BdoType.overline, color = BdoColors.onFaint)
                Spacer(Modifier.height(4.dp))
                Text(formatSilver(row.basePrice), style = BdoType.heroSm, color = BdoColors.goldHi)
            }
            if (spread != null) {
                val sign = if (spread >= 0) "▲" else "▼"
                Text(
                    "$sign %.1f%%".format(kotlin.math.abs(spread)),
                    style = BdoType.num.copy(fontSize = 15.sp),
                    color = if (spread >= 0) BdoColors.up else BdoColors.down,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
            StatRow("In stock", row.stock.toString())
            StatRow("Total trades", formatCompact(row.totalTrades))
            if (row.priceMin > 0 || row.priceMax > 0) {
                StatRow("Price range", "${formatSilver(row.priceMin)} – ${formatSilver(row.priceMax)}")
            }
            if (row.lastSoldPrice > 0) {
                StatRow("Last sold", "${formatSilver(row.lastSoldPrice)} · ${relativeTime(row.lastSoldAt, now)}")
            }
            if (spread != null) {
                val sign = if (spread >= 0) "+" else ""
                StatRow(
                    "Last vs base",
                    "$sign%.1f%%".format(spread),
                    valueColor = if (spread >= 0) Color(0xFF6FBF5A) else Color(0xFFE0593F),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onBackground else valueColor,
        )
    }
}

// ---------------------------------------------------------------------------
// Enhancement-level price table
// ---------------------------------------------------------------------------

@Composable
private fun PriceTable(rows: List<MarketPrice>) {
    val maxSid = rows.maxOf { it.enhancement }
    Column(Modifier.fillMaxWidth()) {
        PriceTableRow(
            label = "ENH",
            price = "PRICE",
            stock = "STOCK",
            lastSold = "LAST SOLD",
            header = true,
        )
        rows.sortedBy { it.enhancement }.forEach { row ->
            PriceTableRow(
                label = enhancementLabel(row.enhancement, maxSid),
                price = formatSilver(row.basePrice),
                stock = row.stock.toString(),
                lastSold = if (row.lastSoldPrice > 0) formatSilver(row.lastSoldPrice) else "—",
            )
        }
    }
}

@Composable
private fun PriceTableRow(
    label: String,
    price: String,
    stock: String,
    lastSold: String,
    header: Boolean = false,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(0.8f), style = style, color = dim, fontWeight = FontWeight.Medium)
        Text(
            price,
            Modifier.weight(1.1f),
            style = style,
            color = if (header) dim else BdoGold,
            fontWeight = if (header) FontWeight.Medium else FontWeight.SemiBold,
        )
        Text(stock, Modifier.weight(0.8f), style = style, color = if (header) dim else MaterialTheme.colorScheme.onBackground)
        Text(lastSold, Modifier.weight(1.1f), style = style, color = dim)
    }
}

// ---------------------------------------------------------------------------
// Price chart: gold line + soft gradient fill, min/max guide lines + labels,
// last-price dot. No axes (range/level chosen via chips above).
// ---------------------------------------------------------------------------

@Composable
private fun PriceHistoryChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    val min = points.minOf { it.price }
    val max = points.maxOf { it.price }
    val last = points.last().price
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Draw-on: the line trims 0→1 via PathMeasure when the data changes.
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)))
    }
    val measure = remember { PathMeasure() }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val span = (max - min).coerceAtLeast(1L).toFloat()
            // 8% vertical padding so the stroke never clips at the extremes.
            fun yOf(price: Long): Float =
                size.height * (0.92f - 0.84f * ((price - min) / span))

            // Faint min/max guide lines.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            listOf(min, max).forEach { p ->
                drawLine(
                    color = dimColor.copy(alpha = 0.25f),
                    start = Offset(0f, yOf(p)),
                    end = Offset(size.width, yOf(p)),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }

            if (points.size == 1) {
                drawCircle(BdoGold, radius = 3.dp.toPx(), center = Offset(size.width / 2f, yOf(points[0].price)))
                return@Canvas
            }

            val stepX = size.width / (points.size - 1)
            val line = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                if (i == 0) line.moveTo(x, yOf(p.price)) else line.lineTo(x, yOf(p.price))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(BdoColors.goldGlow.copy(alpha = 0.45f), Color.Transparent),
                ),
                alpha = progress.value,
            )
            // Trim the line to the animated fraction.
            val drawn = Path()
            measure.setPath(line, false)
            measure.getSegment(0f, measure.length * progress.value, drawn, true)
            // Soft glow underlay + bright line.
            drawPath(drawn, color = BdoColors.goldGlow, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(drawn, color = BdoColors.goldHi, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Last-price marker once fully drawn.
            if (progress.value >= 0.999f) {
                drawCircle(BdoColors.goldHi, radius = 3.5.dp.toPx(), center = Offset(size.width, yOf(last)))
            }
        }
        Text(
            formatSilver(max),
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = dimColor,
        )
        Text(
            formatSilver(min),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = dimColor,
        )
        Text(
            "now ${formatSilver(last)}",
            modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = BdoGold,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
