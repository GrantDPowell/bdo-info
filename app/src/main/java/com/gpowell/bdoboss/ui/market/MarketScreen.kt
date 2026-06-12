package com.gpowell.bdoboss.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.gpowell.bdoboss.ui.formatSilver
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Market tab: watchlist (ITEM favorites with live prices) + local-index name
// search (arsha has no name search — see ItemIndexRepository). Tapping any row
// flips into ItemDetailScreen; detailItemId == null means the main view.
// External requests (Hub favorites) arrive via [externalDetailItemId].
// ---------------------------------------------------------------------------

@Composable
fun MarketScreen(
    market: MarketRepository,
    favoritesRepo: FavoritesRepository,
    itemIndex: ItemIndexRepository,
    externalDetailItemId: Int? = null,
    onExternalDetailConsumed: () -> Unit = {},
) {
    var detailItemId by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(externalDetailItemId) {
        if (externalDetailItemId != null) {
            detailItemId = externalDetailItemId
            onExternalDetailConsumed()
        }
    }

    val detail = detailItemId
    if (detail != null) {
        ItemDetailScreen(
            itemId = detail,
            market = market,
            favoritesRepo = favoritesRepo,
            itemIndex = itemIndex,
            onBack = { detailItemId = null },
        )
    } else {
        MarketMain(
            market = market,
            favoritesRepo = favoritesRepo,
            itemIndex = itemIndex,
            onOpenItem = { detailItemId = it },
        )
    }
}

@Composable
private fun MarketMain(
    market: MarketRepository,
    favoritesRepo: FavoritesRepository,
    itemIndex: ItemIndexRepository,
    onOpenItem: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val favorites by favoritesRepo.favorites.collectAsState(initial = emptyList())
    val watchlist = remember(favorites) {
        favorites.filter { it.type == FavoriteType.ITEM && it.itemId != 0 }
    }

    // Live prices + index grades for the watchlist, one batched call.
    // ArshaSource's 30-min cache makes re-entry cheap.
    var watchPrices by remember { mutableStateOf<Map<Int, MarketPrice>>(emptyMap()) }
    var watchGrades by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var unavailable by remember { mutableStateOf(false) }
    val watchIds = watchlist.map { it.itemId }
    LaunchedEffect(watchIds) {
        if (watchIds.isEmpty()) return@LaunchedEffect
        watchGrades = withContext(Dispatchers.IO) {
            watchIds.associateWith { itemIndex.byId(it)?.grade ?: 0 }
        }
        when (val result = market.prices(watchIds)) {
            is ApiResult.Success -> {
                watchPrices = result.data.associateBy { it.itemId }
                unavailable = false
            }
            // arsha intermittently 500s when Pearl Abyss blocks it; offline same UX.
            is ApiResult.HttpError, ApiResult.Offline -> unavailable = true
            ApiResult.NoKey -> Unit // never happens for arsha
        }
    }

    // Debounced search against the bundled index (loads off-main on first hit).
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<IndexedItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        snapshotFlow { query }.collectLatest { q ->
            if (q.isBlank()) {
                results = emptyList()
                return@collectLatest
            }
            delay(300)
            results = withContext(Dispatchers.IO) { itemIndex.search(q) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (watchlist.isNotEmpty()) {
            item { MarketSectionHeader("WATCHLIST") }
            items(watchlist, key = { it.id }) { fav ->
                WatchlistRow(
                    fav = fav,
                    price = watchPrices[fav.itemId],
                    grade = watchGrades[fav.itemId] ?: 0,
                    onClick = { onOpenItem(fav.itemId) },
                    onRemove = { scope.launch { favoritesRepo.remove(fav.id) } },
                )
            }
            if (unavailable) {
                item {
                    Text(
                        "Marketplace temporarily unavailable — prices may be missing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { MarketSectionHeader("SEARCH") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search the Central Market…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BdoGold,
                    cursorColor = BdoGold,
                    focusedLeadingIconColor = BdoGold,
                ),
            )
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item {
                Text(
                    "No items found.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(results, key = { it.id }) { item ->
            SearchResultRow(item = item, onClick = { onOpenItem(item.id) })
        }
    }
}

@Composable
private fun WatchlistRow(
    fav: Favorite,
    price: MarketPrice?,
    grade: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradeMonogram(name = fav.title, grade = grade, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    fav.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (price != null && price.basePrice > 0) {
                    Text(
                        "${formatSilver(price.basePrice)} · ${price.stock} in stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = BdoGold,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from watchlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: IndexedItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradeMonogram(name = item.name, grade = item.grade, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                item.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(gradeColor(item.grade)),
            )
        }
    }
}

@Composable
internal fun MarketSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = BdoGold,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

// ---------------------------------------------------------------------------
// Grade-colored monogram box — Market results cover arbitrary items with no
// bundled icon art, so this is the universal stand-in. BDO rarity tints:
// 0 white/gray, 1 green, 2 blue, 3 gold, 4 orange/red, 5 "Sovereign" crimson.
// ---------------------------------------------------------------------------

internal fun gradeColor(grade: Int): Color = when (grade) {
    1 -> Color(0xFF6FBF5A)
    2 -> Color(0xFF4FA3E3)
    3 -> BdoGold
    4 -> Color(0xFFE0593F)
    5 -> Color(0xFFD93653)
    else -> Color(0xFFB0ADA6)
}

@Composable
internal fun GradeMonogram(name: String, grade: Int, size: Dp = 36.dp) {
    val color = gradeColor(grade)
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}
