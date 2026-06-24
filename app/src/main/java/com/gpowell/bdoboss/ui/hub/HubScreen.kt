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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.graphics.vector.ImageVector
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
// Hub: launcher (settings/credits + categorized web tools + favorites) that
// flips into the shared in-app browser. Every tool opens in the SAME WebView
// session, so logins/cookies are shared across all of them.
// ---------------------------------------------------------------------------

private data class WebTool(val name: String, val tagline: String, val url: String, val glyph: String)
private data class ToolCategory(val title: String, val tools: List<WebTool>)

private val WEB_TOOLS = listOf(
    ToolCategory(
        "Planners & builds",
        listOf(
            WebTool("Garmoth", "Gear, skill & node planner", "https://garmoth.com/", "GA"),
            WebTool("BDFoundry", "Guides & node calculator", "https://www.blackdesertfoundry.com/", "BF"),
            WebTool("Famme's Tools", "Node, gathering & profit tools", "http://www.somethinglovely.net/bdo/", "FA"),
        ),
    ),
    ToolCategory(
        "Calculators",
        listOf(
            WebTool("Enhance Calc", "Failstacks & enhancement odds", "https://bdolytics.com/en/NA/enhance", "EN"),
            WebTool("Grumpy Green", "Lifeskill & profit calculators", "https://grumpygreen.cricket/", "GG"),
            WebTool("Veliainn", "Cooking & alchemy tools", "https://veliainn.com/", "VE"),
        ),
    ),
    ToolCategory(
        "Databases",
        listOf(
            WebTool("BDO Codex", "Full item & quest database", "https://bdocodex.com/us/", "CX"),
            WebTool("BDOLytics", "Items, recipes & market", "https://bdolytics.com/en/NA", "BL"),
            WebTool("BDO Alerts", "Timers, coupons & tools", "https://bdoalerts.net/", "AL"),
        ),
    ),
    ToolCategory(
        "Maps",
        listOf(
            WebTool("BDOLytics Map", "Nodes, gathering & fishing", "https://bdolytics.com/en/NA/map", "MP"),
            WebTool("BDFoundry Map", "Interactive world map", "https://www.blackdesertfoundry.com/map/", "MP"),
            WebTool("Codex World Map", "Nodes & monster zones", "https://bdocodex.com/us/worldmap/", "MP"),
        ),
    ),
    ToolCategory(
        "Accounts & community",
        listOf(
            WebTool("Discord", "Log in (shared with other tools)", "https://discord.com/login", "DC"),
            WebTool("Official Site", "News, patch notes & login", "https://www.naeu.playblackdesert.com/en-US/", "PA"),
            WebTool("Patch Notes", "Latest updates", "https://www.naeu.playblackdesert.com/en-US/News", "PN"),
            WebTool("r/BlackDesertOnline", "Community subreddit", "https://www.reddit.com/r/blackdesertonline/", "RD"),
        ),
    ),
)

private enum class FavFilter(val label: String, val type: FavoriteType?) {
    ALL("All", null),
    PAGES("Pages", FavoriteType.PAGE),
    ITEMS("Items", FavoriteType.ITEM),
    PLAYERS("Players", FavoriteType.PLAYER),
}

@Composable
fun HubScreen(
    onOpenItem: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    onOpenPlayer: (String) -> Unit = {},
    onOpenGuild: (String) -> Unit = {},
    externalUrl: String? = null,
    onExternalUrlConsumed: () -> Unit = {},
    onExternalBrowserClosed: () -> Unit = {},
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { FavoritesRepository(ctx.applicationContext) }
    val settings = remember { com.gpowell.bdoboss.data.SettingsRepository(ctx.applicationContext) }
    val api = remember { com.gpowell.bdoboss.data.api.BdoAlertsApi(keyProvider = { settings.apiKey() }) }
    var currentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var codexFeature by rememberSaveable { mutableStateOf<com.gpowell.bdoboss.ui.CodexFeature?>(null) }
    // True when the browser was opened from another tab (Events), so closing it returns there.
    var openedExternally by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(externalUrl) {
        if (externalUrl != null) { currentUrl = externalUrl; openedExternally = true; onExternalUrlConsumed() }
    }

    // A favorite/tile url of "codex:FEATURE" opens that in-app Codex screen; anything else
    // opens in the shared browser.
    val openUrlOrCodex: (String) -> Unit = { u ->
        val f = com.gpowell.bdoboss.ui.CodexFeature.fromFavUrl(u)
        if (u.startsWith("codex:") && f != null) codexFeature = f else currentUrl = u
    }

    when {
        currentUrl != null -> BrowserScreen(
            initialUrl = currentUrl!!, repo = repo,
            onExit = {
                currentUrl = null
                if (openedExternally) { openedExternally = false; onExternalBrowserClosed() }
            },
        )
        codexFeature != null -> Column(Modifier.fillMaxSize()) {
            androidx.activity.compose.BackHandler { codexFeature = null }
            HubBackHeader(codexFeature!!.title) { codexFeature = null }
            com.gpowell.bdoboss.ui.CodexFeatureContent(codexFeature!!, api, onOpenUrl = { currentUrl = it })
        }
        else -> HubLauncher(
            repo = repo,
            onOpen = openUrlOrCodex,
            onOpenItem = onOpenItem,
            onOpenSettings = onOpenSettings,
            onOpenCredits = onOpenCredits,
            onOpenCodex = { codexFeature = it },
            onOpenPlayer = onOpenPlayer,
            onOpenGuild = onOpenGuild,
        )
    }
}

@Composable
private fun HubBackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BdoColors.goldHi)
        }
        Text(title, style = BdoType.display.copy(fontSize = 19.sp), color = BdoColors.onBg)
    }
}

@Composable
private fun HubLauncher(
    repo: FavoritesRepository,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onOpenCodex: (com.gpowell.bdoboss.ui.CodexFeature) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenGuild: (String) -> Unit,
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
        // Brand header (no gear — Settings/Credits are cards below)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Diamond(size = 10.dp, glow = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("BDO INFO", style = BdoType.display.copy(fontSize = 20.sp), color = BdoColors.onBg)
                    Text("Hub & web tools · NA", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                }
            }
        }
        // Settings + Credits as two cards
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard("Settings", "Key · theme · effects", Icons.Filled.Settings, Modifier.weight(1f), onOpenSettings)
                AppCard("Credits", "Sources & status", Icons.Filled.Info, Modifier.weight(1f), onOpenCredits)
            }
        }

        // Favorites
        item { SectionLabel("Favorites", Modifier.padding(top = 6.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavFilter.entries.forEach { f ->
                    BdoChip(f.label, active = filter == f, onClick = { filter = f })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Diamond(size = 9.dp, glow = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Nothing saved yet", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                    Text(
                        "Tap ☆ on a boss, item, or page to keep it here.",
                        style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { fav ->
                FavoriteRow(
                    fav = fav, onOpen = onOpen, onOpenItem = onOpenItem,
                    onOpenPlayer = onOpenPlayer, onOpenGuild = onOpenGuild,
                    onDelete = { scope.launch { repo.remove(fav.id) } },
                )
            }
        }

        // Codex — live reference tools (each row opens an in-app screen; ☆ pins the section)
        item { SectionLabel("Codex · live reference", Modifier.padding(top = 8.dp)) }
        items(com.gpowell.bdoboss.ui.CodexFeature.entries, key = { "codex_${it.name}" }) { f ->
            val pinned = favorites.any { fav -> fav.type == FavoriteType.PAGE && fav.url == f.favUrl }
            BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenCodex(f) }, contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.gpowell.bdoboss.ui.theme.Monogram(text = f.glyph, grade = 2, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        Text(f.sub, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    IconButton(onClick = {
                        scope.launch { repo.toggle(FavoriteType.PAGE, title = f.title, subtitle = "Codex", url = f.favUrl) }
                    }) {
                        Icon(
                            if (pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Pin section",
                            tint = if (pinned) BdoColors.goldHi else BdoColors.onFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // All the web links, categorized (all share the in-app browser session)
        WEB_TOOLS.forEach { cat ->
            item { SectionLabel(cat.title, Modifier.padding(top = 6.dp)) }
            items(cat.tools, key = { it.url }) { tool ->
                WebToolRow(tool) { onOpen(tool.url) }
            }
        }
    }
}

@Composable
private fun AppCard(title: String, sub: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    BdoCard(modifier = modifier, facet = true, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Icon(icon, contentDescription = null, tint = BdoColors.goldHi, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = BdoType.display.copy(fontSize = 16.sp), color = BdoColors.onBg)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebToolRow(tool: WebTool, onClick: () -> Unit) {
    BdoCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BdoColors.gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(tool.glyph, style = BdoType.numS.copy(fontSize = 12.sp), color = BdoColors.goldHi)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.name, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tool.tagline, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BdoColors.onFaint)
        }
    }
}

@Composable
private fun FavoriteRow(
    fav: Favorite,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenGuild: (String) -> Unit,
    onDelete: () -> Unit,
) {
    BdoCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            when (fav.type) {
                FavoriteType.PAGE -> if (fav.url.isNotEmpty()) onOpen(fav.url)
                FavoriteType.ITEM -> if (fav.itemId != 0) onOpenItem(fav.itemId) else if (fav.url.isNotEmpty()) onOpen(fav.url)
                FavoriteType.PLAYER -> onOpenPlayer(fav.familyName)
                FavoriteType.GUILD -> onOpenGuild(fav.familyName)
            }
        },
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (fav.type == FavoriteType.ITEM && fav.itemId != 0) {
                com.gpowell.bdoboss.ui.market.ItemIcon(itemId = fav.itemId, name = fav.title, grade = 0, size = 34.dp)
            } else if (fav.type == FavoriteType.PLAYER) {
                com.gpowell.bdoboss.ui.ClassIcon(fav.mainClass.ifBlank { fav.familyName }, size = 34.dp, grade = 3)
            } else {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BdoColors.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (fav.type) {
                            FavoriteType.PAGE -> if (fav.url.startsWith("codex:")) "📖" else "🌐"
                            FavoriteType.ITEM -> "💎"
                            FavoriteType.PLAYER -> "👤"
                            FavoriteType.GUILD -> "⚔"
                        },
                        fontSize = 17.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fav.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fav.subtitle.isNotBlank()) {
                    Text(fav.subtitle, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove favorite", tint = BdoColors.onFaint)
            }
        }
    }
}
