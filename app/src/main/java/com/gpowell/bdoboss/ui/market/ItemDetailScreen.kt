package com.gpowell.bdoboss.ui.market

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Item detail: enhancement-level price table (itemAll) + 90-day history chart
// (history) + ⭐ watchlist toggle (unified favorites store, type ITEM).
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
        history = when (val result = market.history(itemId)) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    val name = rows?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: indexed?.name
        ?: "Item $itemId"

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
            GradeMonogram(name = name, grade = indexed?.grade ?: 0, size = 36.dp)
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
        MarketSectionHeader("PRICE HISTORY (90D)")
        Spacer(Modifier.height(8.dp))
        val points = history
        when {
            points == null -> DimLine("Loading history…")
            points.isEmpty() -> DimLine("No history available.")
            else -> PriceHistoryChart(
                points = points,
                modifier = Modifier.fillMaxWidth().height(160.dp),
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

@Composable
private fun DimLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
// 90-day price chart: gold line + soft gradient fill, min/max labels, no axes.
// ---------------------------------------------------------------------------

@Composable
private fun PriceHistoryChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    val min = points.minOf { it.price }
    val max = points.maxOf { it.price }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val span = (max - min).coerceAtLeast(1L).toFloat()
            // 8% vertical padding so the stroke never clips at the extremes.
            fun yOf(price: Long): Float =
                size.height * (0.92f - 0.84f * ((price - min) / span))

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
                    listOf(BdoGold.copy(alpha = 0.25f), Color.Transparent),
                ),
            )
            drawPath(
                line,
                color = BdoGold,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Text(
            formatSilver(max),
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            formatSilver(min),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
