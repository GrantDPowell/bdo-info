package com.gpowell.bdoboss.ui.hub

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.SectionLabel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Hub tab: launcher view (site tiles + saved favorites) that flips into an
// in-app browser (BrowserScreen) when a url is opened. currentUrl == null
// means launcher; non-null means browser.
// ---------------------------------------------------------------------------

private data class HubSite(val name: String, val tagline: String, val url: String, val glyph: String)

private val HUB_SITES = listOf(
    HubSite("BDO Codex", "Item database", "https://bdocodex.com/us/", "CX"),
    HubSite("BDO Alerts", "Timers & tools", "https://bdoalerts.net/", "AL"),
    HubSite("Garmoth", "Builds & maps", "https://garmoth.com/", "GA"),
)

private enum class FavFilter(val label: String, val type: FavoriteType?) {
    ALL("All", null),
    PAGES("Pages", FavoriteType.PAGE),
    ITEMS("Items", FavoriteType.ITEM),
    PLAYERS("Players", FavoriteType.PLAYER),
}

@Composable
fun HubScreen(onOpenItem: (Int) -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FavoritesRepository(ctx.applicationContext) }
    var currentUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val url = currentUrl
    if (url == null) {
        HubLauncher(repo = repo, onOpen = { currentUrl = it }, onOpenItem = onOpenItem)
    } else {
        BrowserScreen(initialUrl = url, repo = repo, onExit = { currentUrl = null })
    }
}

@Composable
private fun HubLauncher(
    repo: FavoritesRepository,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
) {
    val favorites by repo.favorites.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(FavFilter.ALL) }
    val scope = rememberCoroutineScope()

    val filtered = remember(favorites, filter) {
        filter.type?.let { t -> favorites.filter { it.type == t } } ?: favorites
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionLabel("Web hub") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HUB_SITES.forEach { site ->
                    SiteTile(site, Modifier.weight(1f)) { onOpen(site.url) }
                }
            }
        }
        item { SectionLabel("Favorites", Modifier.padding(top = 8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavFilter.entries.forEach { f ->
                    BdoChip(f.label, active = filter == f, onClick = { filter = f })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Diamond(size = 10.dp, glow = true)
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing saved yet", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap ☆ on a boss, item, or page to keep it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BdoColors.onFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { fav ->
                FavoriteRow(
                    fav = fav,
                    onOpen = onOpen,
                    onOpenItem = onOpenItem,
                    onDelete = { scope.launch { repo.remove(fav.id) } },
                )
            }
        }
    }
}

@Composable
private fun SiteTile(site: HubSite, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BdoCard(
        modifier = modifier,
        facet = true,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BdoColors.gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(site.glyph, style = BdoType.numS.copy(fontSize = 13.sp), color = BdoColors.goldHi)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                site.name,
                style = BdoType.display.copy(fontSize = 15.sp),
                color = BdoColors.onBg,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                site.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = BdoColors.onFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    fav: Favorite,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    BdoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            when (fav.type) {
                FavoriteType.PAGE -> if (fav.url.isNotEmpty()) onOpen(fav.url)
                FavoriteType.ITEM -> if (fav.itemId != 0) onOpenItem(fav.itemId) else if (fav.url.isNotEmpty()) onOpen(fav.url)
                FavoriteType.PLAYER -> Unit
            }
        },
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (fav.type == FavoriteType.ITEM && fav.itemId != 0) {
                // Real (cached) Central Market icon, same source as the Market tab.
                com.gpowell.bdoboss.ui.market.ItemIcon(
                    itemId = fav.itemId,
                    name = fav.title,
                    grade = 0,
                    size = 34.dp,
                )
            } else {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BdoColors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (fav.type) {
                            FavoriteType.PAGE -> "🌐"
                            FavoriteType.ITEM -> "💎"
                            FavoriteType.PLAYER -> "👤"
                        },
                        fontSize = 17.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    fav.title,
                    fontWeight = FontWeight.SemiBold,
                    color = BdoColors.onBg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fav.subtitle.isNotBlank()) {
                    Text(
                        fav.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BdoColors.onFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove favorite", tint = BdoColors.onFaint)
            }
        }
    }
}
