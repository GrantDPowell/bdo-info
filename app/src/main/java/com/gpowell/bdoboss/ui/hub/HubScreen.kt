package com.gpowell.bdoboss.ui.hub

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Hub tab: launcher view (site tiles + saved favorites) that flips into an
// in-app browser (BrowserScreen) when a url is opened. currentUrl == null
// means launcher; non-null means browser.
// ---------------------------------------------------------------------------

private data class HubSite(val name: String, val tagline: String, val url: String)

private val HUB_SITES = listOf(
    HubSite("BDO Codex", "Item database", "https://bdocodex.com/us/"),
    HubSite("BDO Alerts", "Timers & tools", "https://bdoalerts.net/"),
    HubSite("Garmoth", "Companion & builds", "https://garmoth.com/"),
)

private enum class FavFilter(val label: String, val type: FavoriteType?) {
    ALL("All", null),
    PAGES("Pages", FavoriteType.PAGE),
    ITEMS("Items", FavoriteType.ITEM),
    PLAYERS("Players", FavoriteType.PLAYER),
}

@Composable
fun HubScreen(onOpenItem: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { HubSectionHeader("WEB HUB") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HUB_SITES.forEach { site ->
                    SiteTile(site, Modifier.weight(1f)) { onOpen(site.url) }
                }
            }
        }
        item { HubSectionHeader("FAVORITES") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BdoGold,
                            selectedLabelColor = Color.Black,
                        ),
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    "Nothing saved yet — tap ☆ anywhere to keep things here.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteTile(site: HubSite, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Same press-scale pattern as SpawnCard (TimersScreen).
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                site.name,
                style = MaterialTheme.typography.titleSmall,
                color = BdoGold,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                site.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteRow(
    fav: Favorite,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = {
            when (fav.type) {
                // PAGE always carries a url.
                FavoriteType.PAGE -> if (fav.url.isNotEmpty()) onOpen(fav.url)
                // ITEM with an itemId opens Market item detail (cross-tab);
                // codex-page saves without an id fall back to the browser.
                FavoriteType.ITEM -> if (fav.itemId != 0) {
                    onOpenItem(fav.itemId)
                } else if (fav.url.isNotEmpty()) {
                    onOpen(fav.url)
                }
                // PLAYER: Profile deep links land in a later task.
                FavoriteType.PLAYER -> Unit
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (fav.type) {
                    FavoriteType.PAGE -> "🌐"
                    FavoriteType.ITEM -> "💎"
                    FavoriteType.PLAYER -> "👤"
                },
                fontSize = 18.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    fav.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fav.subtitle.isNotBlank()) {
                    Text(
                        fav.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove favorite",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HubSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = BdoGold,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}
