package com.gpowell.bdoboss.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.ui.ClassIcon
import com.gpowell.bdoboss.ui.CodexFeature
import com.gpowell.bdoboss.ui.CodexFeatureContent
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.Monogram
import com.gpowell.bdoboss.ui.theme.SectionLabel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Hub = a "home" of section cards (Favorites · Codex · Web Tools · Settings ·
// Credits), each opening a focused sub-screen. Everything that opens a web page
// shares the SAME in-app WebView session (cookies/logins shared).
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

private enum class FavFilter(val label: String, val types: Set<FavoriteType>?) {
    ALL("All", null),
    PLAYERS("Players", setOf(FavoriteType.PLAYER)),
    GUILDS("Guilds", setOf(FavoriteType.GUILD)),
    ITEMS("Items", setOf(FavoriteType.ITEM)),
    PAGES("Pages", setOf(FavoriteType.PAGE)),
}

private enum class HubView { HOME, FAVORITES, CODEX, WEBTOOLS, MAP }

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
    val scope = rememberCoroutineScope()

    var view by rememberSaveable { mutableStateOf(HubView.HOME) }
    var currentUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var codexFeature by rememberSaveable { mutableStateOf<CodexFeature?>(null) }
    var openedExternally by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(externalUrl) {
        if (externalUrl != null) { currentUrl = externalUrl; openedExternally = true; onExternalUrlConsumed() }
    }

    val openUrlOrCodex: (String) -> Unit = { u ->
        val f = CodexFeature.fromFavUrl(u)
        if (u.startsWith("codex:") && f != null) codexFeature = f else currentUrl = u
    }
    val favoritePage: (String, String) -> Unit = { title, url ->
        scope.launch { repo.toggle(FavoriteType.PAGE, title = title, subtitle = "Saved", url = url) }
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
            BackHandler { codexFeature = null }
            HubBackHeader(codexFeature!!.title) { codexFeature = null }
            CodexFeatureContent(codexFeature!!, api, onOpenUrl = { currentUrl = it }, onFavorite = favoritePage)
        }
        view == HubView.FAVORITES -> {
            BackHandler { view = HubView.HOME }
            FavoritesScreen(repo, onBack = { view = HubView.HOME }, onOpen = openUrlOrCodex, onOpenItem = onOpenItem, onOpenPlayer = onOpenPlayer, onOpenGuild = onOpenGuild)
        }
        view == HubView.CODEX -> {
            BackHandler { view = HubView.HOME }
            CodexLanding(repo, onBack = { view = HubView.HOME }, onPick = { codexFeature = it })
        }
        view == HubView.WEBTOOLS -> {
            BackHandler { view = HubView.HOME }
            WebToolsScreen(onBack = { view = HubView.HOME }, onOpen = { currentUrl = it })
        }
        view == HubView.MAP -> com.gpowell.bdoboss.ui.MapScreen(onBack = { view = HubView.HOME })
        else -> HubHome(
            repo = repo,
            onFavorites = { view = HubView.FAVORITES },
            onMap = { view = HubView.MAP },
            onCodex = { view = HubView.CODEX },
            onWebTools = { view = HubView.WEBTOOLS },
            onSettings = onOpenSettings,
            onCredits = onOpenCredits,
        )
    }
}

// ── Home ──────────────────────────────────────────────────────────────────────
@Composable
private fun HubHome(
    repo: FavoritesRepository,
    onFavorites: () -> Unit,
    onMap: () -> Unit,
    onCodex: () -> Unit,
    onWebTools: () -> Unit,
    onSettings: () -> Unit,
    onCredits: () -> Unit,
) {
    val favCount = repo.favorites.collectAsState(initial = emptyList()).value.size
    val webCount = WEB_TOOLS.sumOf { it.tools.size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Diamond(size = 10.dp, glow = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("BDO INFO", style = BdoType.display.copy(fontSize = 20.sp), color = BdoColors.onBg)
                    Text("Hub · tools, favorites & reference", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                }
            }
        }
        item { SectionCard(Icons.Outlined.Bookmark, "Favorites", if (favCount == 0) "Nothing saved yet" else "$favCount saved · players, guilds, items, pages", onFavorites) }
        item { SectionCard(Icons.Outlined.Map, "World Map", "Offline pan & zoom map of the BDO world", onMap) }
        item { SectionCard(Icons.Outlined.AutoStories, "Codex", "Leaderboards, grind spots, cron, lightstones, skills…", onCodex) }
        item { SectionCard(Icons.Outlined.Public, "Web Tools", "$webCount sites · planners, calculators, maps (shared login)", onWebTools) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard("Settings", "Key · theme · effects", Icons.Filled.Settings, Modifier.weight(1f), onSettings)
                AppCard("Credits", "Sources & status", Icons.Filled.Info, Modifier.weight(1f), onCredits)
            }
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(BdoColors.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = BdoColors.goldHi, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = BdoType.display.copy(fontSize = 18.sp), color = BdoColors.onBg)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BdoColors.onFaint)
        }
    }
}

// ── Favorites (search + filter + grouped) ─────────────────────────────────────
@Composable
private fun FavoritesScreen(
    repo: FavoritesRepository,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenItem: (Int) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenGuild: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val all by repo.favorites.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(FavFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }

    val types = filter.types
    val filtered = all
        .filter { types == null || it.type in types }
        .filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        HubBackHeader("Favorites", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search favorites") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BdoColors.gold, cursorColor = BdoColors.gold, focusedLeadingIconColor = BdoColors.gold),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FavFilter.entries.forEach { f -> BdoChip(f.label, active = filter == f, onClick = { filter = f }) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Diamond(size = 9.dp, glow = true)
                    Spacer(Modifier.height(10.dp))
                    Text(if (all.isEmpty()) "Nothing saved yet" else "No matches", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                    Text("Tap ☆ on a player, guild, item, page, or Codex section to keep it here.", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered, key = { it.id }) { fav ->
                    FavoriteRow(
                        fav = fav,
                        onClick = {
                            when (fav.type) {
                                FavoriteType.PAGE -> if (fav.url.isNotEmpty()) onOpen(fav.url)
                                FavoriteType.ITEM -> if (fav.itemId != 0) onOpenItem(fav.itemId) else if (fav.url.isNotEmpty()) onOpen(fav.url)
                                FavoriteType.PLAYER -> onOpenPlayer(fav.familyName)
                                FavoriteType.GUILD -> onOpenGuild(fav.familyName)
                            }
                        },
                        onDelete = { scope.launch { repo.remove(fav.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(fav: Favorite, onClick: () -> Unit, onDelete: () -> Unit) {
    BdoCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when {
                fav.type == FavoriteType.ITEM && fav.itemId != 0 ->
                    com.gpowell.bdoboss.ui.market.ItemIcon(itemId = fav.itemId, name = fav.title, grade = 0, size = 34.dp)
                fav.type == FavoriteType.PLAYER ->
                    ClassIcon(fav.mainClass.ifBlank { fav.familyName }, size = 34.dp, grade = 3)
                else -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BdoColors.surface2), contentAlignment = Alignment.Center) {
                    Text(
                        when (fav.type) {
                            FavoriteType.PAGE -> if (fav.url.startsWith("codex:")) "📖" else "🌐"
                            FavoriteType.GUILD -> "⚔"
                            else -> "💎"
                        },
                        fontSize = 17.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fav.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fav.subtitle.isNotBlank()) Text(fav.subtitle, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Remove", tint = BdoColors.onFaint, modifier = Modifier.size(18.dp)) }
        }
    }
}

// ── Codex landing ─────────────────────────────────────────────────────────────
@Composable
private fun CodexLanding(repo: FavoritesRepository, onBack: () -> Unit, onPick: (CodexFeature) -> Unit) {
    val scope = rememberCoroutineScope()
    val favorites by repo.favorites.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize()) {
        HubBackHeader("Codex", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Live BDO reference · NA", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint) }
            items(CodexFeature.entries, key = { it.name }) { f ->
                val pinned = favorites.any { it.type == FavoriteType.PAGE && it.url == f.favUrl }
                BdoCard(Modifier.fillMaxWidth(), onClick = { onPick(f) }, contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(text = f.glyph, grade = 2, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                            Text(f.sub, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                        }
                        IconButton(onClick = { scope.launch { repo.toggle(FavoriteType.PAGE, title = f.title, subtitle = "Codex", url = f.favUrl) } }) {
                            Icon(if (pinned) Icons.Filled.Star else Icons.Outlined.StarBorder, "Pin", tint = if (pinned) BdoColors.goldHi else BdoColors.onFaint, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Web tools ─────────────────────────────────────────────────────────────────
@Composable
private fun WebToolsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HubBackHeader("Web Tools", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("All share one in-app browser session (logins persist).", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint) }
            WEB_TOOLS.forEach { cat ->
                item { SectionLabel(cat.title, Modifier.padding(top = 6.dp)) }
                items(cat.tools, key = { it.url }) { tool -> WebToolRow(tool) { onOpen(tool.url) } }
            }
        }
    }
}

// ── shared bits ───────────────────────────────────────────────────────────────
@Composable
private fun HubBackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BdoColors.goldHi) }
        Text(title, style = BdoType.display.copy(fontSize = 19.sp), color = BdoColors.onBg)
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
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BdoColors.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
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
