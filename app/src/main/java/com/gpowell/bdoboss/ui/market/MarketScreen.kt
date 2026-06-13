package com.gpowell.bdoboss.ui.market

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.market.IndexedItem
import com.gpowell.bdoboss.data.market.ItemIndexRepository
import com.gpowell.bdoboss.data.market.MarketCategory
import com.gpowell.bdoboss.data.market.MarketCategoryRepository
import com.gpowell.bdoboss.data.market.MarketListing
import com.gpowell.bdoboss.data.market.MarketPrice
import com.gpowell.bdoboss.data.market.MarketRepository
import com.gpowell.bdoboss.ui.formatSilver
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoGold
import com.gpowell.bdoboss.ui.theme.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Market tab. Self-navigating across four views via three saveable Int? states:
//   detailItemId != null         -> ItemDetailScreen (overrides all)
//   browseMain & browseSub set    -> category item list (GetWorldMarketList)
//   browseMain set                -> subcategory list
//   else                          -> main view (watchlist + search + category grid)
// arsha has no name search, so search hits the bundled index; live prices/listings
// come from ids/categories. External requests (Hub favorites) arrive via
// [externalDetailItemId].
// ---------------------------------------------------------------------------

@Composable
fun MarketScreen(
    market: MarketRepository,
    favoritesRepo: FavoritesRepository,
    itemIndex: ItemIndexRepository,
    externalDetailItemId: Int? = null,
    onExternalDetailConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val categoryRepo = remember { MarketCategoryRepository(ctx.applicationContext) }

    var detailItemId by rememberSaveable { mutableStateOf<Int?>(null) }
    var browseMain by rememberSaveable { mutableStateOf<Int?>(null) }
    var browseSub by rememberSaveable { mutableStateOf<Int?>(null) }

    // Category tree loaded once off-main.
    var categories by remember { mutableStateOf<List<MarketCategory>>(emptyList()) }
    LaunchedEffect(Unit) {
        categories = withContext(Dispatchers.IO) { categoryRepo.categories() }
    }

    LaunchedEffect(externalDetailItemId) {
        if (externalDetailItemId != null) {
            detailItemId = externalDetailItemId
            onExternalDetailConsumed()
        }
    }

    // Back navigation across the browse stack (detail screens own their back).
    BackHandler(enabled = detailItemId == null && browseMain != null) {
        if (browseSub != null) browseSub = null else browseMain = null
    }

    val detail = detailItemId
    val main = browseMain
    val sub = browseSub
    when {
        detail != null -> ItemDetailScreen(
            itemId = detail,
            market = market,
            favoritesRepo = favoritesRepo,
            itemIndex = itemIndex,
            onBack = { detailItemId = null },
        )

        main != null && sub != null -> {
            val cat = categories.firstOrNull { it.code == main }
            CategoryItemsScreen(
                market = market,
                itemIndex = itemIndex,
                mainName = cat?.name ?: "Category",
                subName = cat?.subs?.firstOrNull { it.code == sub }?.name ?: "Items",
                mainCategory = main,
                subCategory = sub,
                onOpenItem = { detailItemId = it },
                onBack = { browseSub = null },
            )
        }

        main != null -> {
            val cat = categories.firstOrNull { it.code == main }
            SubCategoryScreen(
                category = cat,
                onPickSub = { browseSub = it },
                onBack = { browseMain = null },
            )
        }

        else -> MarketMain(
            market = market,
            favoritesRepo = favoritesRepo,
            itemIndex = itemIndex,
            categories = categories,
            onOpenItem = { detailItemId = it },
            onPickCategory = { browseMain = it },
        )
    }
}

@Composable
private fun MarketMain(
    market: MarketRepository,
    favoritesRepo: FavoritesRepository,
    itemIndex: ItemIndexRepository,
    categories: List<MarketCategory>,
    onOpenItem: (Int) -> Unit,
    onPickCategory: (Int) -> Unit,
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

    // Watchlist sort.
    var sort by rememberSaveable { mutableStateOf(WatchSort.RECENT) }
    val sortedWatch = remember(watchlist, watchPrices, sort) {
        sortWatchlist(watchlist, watchPrices, sort)
    }
    val terminalItems = remember(sortedWatch, watchPrices, watchGrades) {
        sortedWatch.map { fav -> TerminalItem(fav, watchPrices[fav.itemId], watchGrades[fav.itemId] ?: 0) }
    }
    // Spotlight cycles through the watchlist every 5s (most-moving first).
    val spotlightOrder = remember(terminalItems) {
        terminalItems.sortedByDescending { kotlin.math.abs(it.spread) }
    }
    var spotIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(spotlightOrder.size) {
        if (spotlightOrder.size <= 1) { spotIndex = 0; return@LaunchedEffect }
        while (true) {
            delay(5000)
            spotIndex = (spotIndex + 1) % spotlightOrder.size
        }
    }
    val spotlight = spotlightOrder.getOrNull(
        if (spotlightOrder.isEmpty()) 0 else spotIndex % spotlightOrder.size,
    )

    // Target-setting dialog target (the favorite being edited), null = closed.
    var targetFor by remember { mutableStateOf<Favorite?>(null) }

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
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "No items found.",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(results, key = { it.id }) { item ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SearchResultRow(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        } else {
            if (watchlist.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Your watchlist is empty", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Search above and tap ⭐ on an item to track its price here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BdoColors.onFaint,
                        )
                    }
                }
            } else {
                if (spotlight != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        AnimatedContent(
                            targetState = spotlight,
                            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                            label = "spotlight",
                        ) { sp ->
                            Spotlight(sp) { onOpenItem(sp.fav.itemId) }
                        }
                    }
                }
                item {
                    SectionLabel(
                        "Watchlist",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                        trailing = { WatchSortChips(sort) { sort = it } },
                    )
                }
                item {
                    BdoCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        terminalItems.forEachIndexed { i, ti ->
                            SparkRow(
                                item = ti,
                                onOpen = { onOpenItem(ti.fav.itemId) },
                                onLong = { targetFor = ti.fav },
                                isLast = i == terminalItems.lastIndex,
                            )
                        }
                    }
                }
                if (unavailable) {
                    item {
                        Text(
                            "Marketplace temporarily unavailable — prices may be missing",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Category browser always shows when not searching (whether or not the
            // watchlist has items) — a full 2-column grid, not a cramped rail.
            if (categories.isNotEmpty()) {
                item { SectionLabel("Browse by category", Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)) }
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        CategoryGrid(categories, onPickCategory)
                    }
                }
            }
        }
    }

    targetFor?.let { fav ->
        TargetDialog(
            fav = fav,
            onDismiss = { targetFor = null },
            onConfirm = { silver ->
                scope.launch {
                    favoritesRepo.setTarget(fav.itemId, silver, title = fav.title, url = fav.url)
                }
                targetFor = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Watchlist row + sorting + target dialog
// ---------------------------------------------------------------------------

internal enum class WatchSort(val label: String) {
    RECENT("Recent"), NAME("Name"), PRICE("Price"), TRADES("Volume")
}

/** Pure (testable) watchlist ordering. Items without a live price sink to the bottom. */
internal fun sortWatchlist(
    watchlist: List<Favorite>,
    prices: Map<Int, MarketPrice>,
    sort: WatchSort,
): List<Favorite> = when (sort) {
    WatchSort.RECENT -> watchlist // already newest-first from the repo
    WatchSort.NAME -> watchlist.sortedBy { it.title.lowercase() }
    WatchSort.PRICE -> watchlist.sortedByDescending { prices[it.itemId]?.basePrice ?: -1L }
    WatchSort.TRADES -> watchlist.sortedByDescending { prices[it.itemId]?.totalTrades ?: -1L }
}

@Composable
private fun WatchSortChips(sort: WatchSort, onChange: (WatchSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WatchSort.entries.forEach { option ->
            FilterChip(
                selected = sort == option,
                onClick = { onChange(option) },
                label = { Text(option.label, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BdoGold.copy(alpha = 0.22f),
                    selectedLabelColor = BdoGold,
                ),
            )
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
    onSetTarget: () -> Unit,
) {
    val targetHit = fav.targetPrice > 0 && price != null &&
        price.basePrice in 1..fav.targetPrice
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemIcon(itemId = fav.itemId, name = fav.title, grade = grade, size = 36.dp)
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
                        "${formatSilver(price.basePrice)} · ${price.stock} in stock" +
                            if (price.totalTrades > 0) " · ${formatCompact(price.totalTrades)} trades" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = BdoGold,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (fav.targetPrice > 0) {
                    Text(
                        if (targetHit) "🎯 target ${formatSilver(fav.targetPrice)} — HIT"
                        else "🎯 target ${formatSilver(fav.targetPrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (targetHit) Color(0xFF6FBF5A) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (targetHit) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            TextButton(onClick = onSetTarget) {
                Text(if (fav.targetPrice > 0) "Edit ⌖" else "Set ⌖", fontSize = 11.sp, color = BdoGold)
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
private fun TargetDialog(
    fav: Favorite,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(if (fav.targetPrice > 0) fav.targetPrice.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buy target — ${fav.title}", maxLines = 2) },
        text = {
            Column {
                Text(
                    "Flag this item when its lowest price drops to or below your target. " +
                        "Checked when you open the Market (no background alerts).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() } },
                    label = { Text("Target price (silver)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BdoGold,
                        focusedLabelColor = BdoGold,
                        cursorColor = BdoGold,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toLongOrNull() ?: 0L) }) {
                Text(if (text.isBlank()) "Clear" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchResultRow(item: IndexedItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemIcon(itemId = item.id, name = item.name, grade = item.grade, size = 32.dp)
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

// ---------------------------------------------------------------------------
// Category browser: grid of mains -> subcategory list -> item list
// ---------------------------------------------------------------------------

@Composable
private fun CategoryGrid(categories: List<MarketCategory>, onPick: (Int) -> Unit) {
    // A non-scrolling grid sized to its content (it lives inside the parent LazyColumn).
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(((categories.size + 1) / 2 * 52).dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(categories, key = { it.code }) { cat ->
            Card(onClick = { onPick(cat.code) }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cat.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = BdoGold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubCategoryScreen(
    category: MarketCategory?,
    onPickSub: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BrowseTopBar(title = category?.name ?: "Category", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(category?.subs.orEmpty(), key = { it.code }) { subCat ->
                Card(onClick = { onPickSub(subCat.code) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            subCat.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = BdoGold,
                        )
                    }
                }
            }
        }
    }
}

private enum class ListingSort(val label: String) {
    PRICE("Price"), TRADES("Volume"), NAME("Name")
}

@Composable
private fun CategoryItemsScreen(
    market: MarketRepository,
    itemIndex: ItemIndexRepository,
    mainName: String,
    subName: String,
    mainCategory: Int,
    subCategory: Int,
    onOpenItem: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var listings by remember(mainCategory, subCategory) { mutableStateOf<List<MarketListing>?>(null) }
    var unavailable by remember(mainCategory, subCategory) { mutableStateOf(false) }
    var grades by remember(mainCategory, subCategory) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var sort by rememberSaveable { mutableStateOf(ListingSort.PRICE) }

    LaunchedEffect(mainCategory, subCategory) {
        when (val result = market.categoryList(mainCategory, subCategory)) {
            is ApiResult.Success -> {
                listings = result.data
                unavailable = false
                grades = withContext(Dispatchers.IO) {
                    result.data.associate { it.itemId to (itemIndex.byId(it.itemId)?.grade ?: 0) }
                }
            }
            else -> {
                listings = emptyList()
                unavailable = true
            }
        }
    }

    val rows = listings
    val sorted = remember(rows, sort) {
        when (sort) {
            ListingSort.PRICE -> rows?.sortedByDescending { it.basePrice }
            ListingSort.TRADES -> rows?.sortedByDescending { it.totalTrades }
            ListingSort.NAME -> rows?.sortedBy { it.name.lowercase() }
        }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        BrowseTopBar(title = "$mainName · $subName", onBack = onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ListingSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = { Text(option.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BdoGold.copy(alpha = 0.22f),
                        selectedLabelColor = BdoGold,
                    ),
                )
            }
        }
        when {
            rows == null -> CenterLine("Loading category…")
            unavailable -> CenterLine("Marketplace temporarily unavailable — try again shortly.")
            rows.isEmpty() -> CenterLine("No items in this category.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.itemId }) { listing ->
                    ListingRow(
                        listing = listing,
                        grade = grades[listing.itemId] ?: 0,
                        onClick = { onOpenItem(listing.itemId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListingRow(listing: MarketListing, grade: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemIcon(itemId = listing.itemId, name = listing.name, grade = grade, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listing.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${listing.stock} in stock" +
                        if (listing.totalTrades > 0) " · ${formatCompact(listing.totalTrades)} trades" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (listing.basePrice > 0) formatSilver(listing.basePrice) else "—",
                style = MaterialTheme.typography.bodyMedium,
                color = BdoGold,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BrowseTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BdoGold)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenterLine(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
// Shared formatters
// ---------------------------------------------------------------------------

/** Compact count: 682271935 -> "682.3M", 1630399 -> "1.6M", 543 -> "543". */
internal fun formatCompact(n: Long): String = when {
    n >= 1_000_000_000L -> "%.1fB".format(n / 1_000_000_000.0)
    n >= 1_000_000L -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000L -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
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
