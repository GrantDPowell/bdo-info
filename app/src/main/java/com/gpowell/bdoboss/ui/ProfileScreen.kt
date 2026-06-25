package com.gpowell.bdoboss.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.data.Favorite
import com.gpowell.bdoboss.data.FavoriteType
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.data.api.GuildProfile
import com.gpowell.bdoboss.data.api.GuildSearchResult
import com.gpowell.bdoboss.data.api.PlayerProfile
import com.gpowell.bdoboss.data.api.PlayerSearchResult
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoSubTabs
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.Monogram
import com.gpowell.bdoboss.ui.theme.SectionLabel
import kotlinx.coroutines.launch

private const val REGION_P = "na"

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    externalPlayer: String? = null,
    externalGuild: String? = null,
    onExternalConsumed: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val settings = remember { SettingsRepository(ctx.applicationContext) }
    val favs = remember { FavoritesRepository(ctx.applicationContext) }
    val apiKey by settings.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Adventurer Profile",
            blurb = "Favorite players & guilds and look anyone up: gear, lifeskills, characters & member rosters — via BDO Alerts.",
            bullets = listOf("Favorite multiple players & guilds", "Gear, lifeskills & characters", "Guild master & roster"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { settings.apiKey() }) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var viewPlayer by rememberSaveable { mutableStateOf<String?>(null) }
    var viewGuild by rememberSaveable { mutableStateOf<String?>(null) }

    // Opened from the Hub favorites (cross-tab): show that detail.
    LaunchedEffect(externalPlayer, externalGuild) {
        if (externalPlayer != null) { viewGuild = null; viewPlayer = externalPlayer; onExternalConsumed() }
        else if (externalGuild != null) { viewPlayer = null; viewGuild = externalGuild; onExternalConsumed() }
    }
    androidx.activity.compose.BackHandler(enabled = viewPlayer != null || viewGuild != null) {
        viewPlayer = null; viewGuild = null
    }

    when {
        viewPlayer != null -> PlayerDetail(api, favs, viewPlayer!!) { viewPlayer = null }
        viewGuild != null -> GuildDetail(api, favs, viewGuild!!) { viewGuild = null }
        else -> Column(Modifier.fillMaxSize()) {
            BdoSubTabs(
                tabs = listOf("Favorites", "Search", "Guilds"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            when (tab) {
                0 -> FavoritesTab(favs, api, onOpenPlayer = { viewPlayer = it }, onOpenGuild = { viewGuild = it })
                1 -> SearchTab(api) { viewPlayer = it }
                else -> GuildsTab(api) { viewGuild = it }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BdoColors.goldHi) }
        Text(title, style = BdoType.display.copy(fontSize = 19.sp), color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Favorites ─────────────────────────────────────────────────────────────────
@Composable
private fun FavoritesTab(repo: FavoritesRepository, api: BdoAlertsApi, onOpenPlayer: (String) -> Unit, onOpenGuild: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val all by repo.favorites.collectAsState(initial = emptyList())
    val players = all.filter { it.type == FavoriteType.PLAYER }
    val guilds = all.filter { it.type == FavoriteType.GUILD }

    // Fill in missing class portraits for favorited players (e.g. the seeded OkimaSha) by
    // fetching each once and remembering the main class — so the list shows their portrait.
    val attempted = remember { mutableSetOf<String>() }
    LaunchedEffect(players.size) {
        players.filter { it.mainClass.isBlank() && attempted.add(it.familyName.lowercase()) }.forEach { f ->
            (api.playerProfile(REGION_P, f.familyName) as? ApiResult.Success)?.let { p ->
                val mc = p.data.characters.firstOrNull { it.isMain }?.className
                    ?: p.data.characters.firstOrNull()?.className ?: ""
                repo.updateMainClass(REGION_P, f.familyName, mc)
            }
        }
    }

    if (players.isEmpty() && guilds.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Diamond(size = 9.dp, glow = true)
                Spacer(Modifier.height(10.dp))
                Text("No favorites yet", style = MaterialTheme.typography.titleSmall, color = BdoColors.onBg)
                Text("Search a player or guild, then ☆ to keep them here.", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (players.isNotEmpty()) {
            item { SectionLabel("Players") }
            items(players, key = { it.id }) { f ->
                FavRow(f, isPlayer = true, onOpen = { onOpenPlayer(f.familyName) }, onRemove = { scope.launch { repo.remove(f.id) } })
            }
        }
        if (guilds.isNotEmpty()) {
            item { SectionLabel("Guilds", Modifier.padding(top = 6.dp)) }
            items(guilds, key = { it.id }) { f ->
                FavRow(f, isPlayer = false, onOpen = { onOpenGuild(f.familyName) }, onRemove = { scope.launch { repo.remove(f.id) } })
            }
        }
    }
}

@Composable
private fun FavRow(f: Favorite, isPlayer: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    BdoCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPlayer) {
                ClassIcon(f.mainClass.ifBlank { f.familyName }, size = 40.dp, grade = 3)
            } else {
                Monogram(text = f.familyName.take(2).uppercase(), grade = 2, size = 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.familyName, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                if (f.subtitle.isNotBlank()) Text(f.subtitle, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove", tint = BdoColors.onFaint, modifier = Modifier.size(18.dp)) }
        }
    }
}

// ── Search (players) ──────────────────────────────────────────────────────────
@Composable
private fun SearchTab(api: BdoAlertsApi, onOpenPlayer: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlayerSearchResult>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        searched = true
        suggestions = (api.playerSearch(REGION_P, query.trim()) as? ApiResult.Success)?.data ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchField(query, { query = it; searched = false }, "Family name (NA)") { onOpenPlayer(query.trim()) }
        Spacer(Modifier.height(8.dp))
        if (suggestions.isEmpty() && searched && query.trim().length >= 2) {
            CenterHint("No player found for \"${query.trim()}\".")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(suggestions, key = { it.familyName }) { s ->
                    BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenPlayer(s.familyName) }, contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClassIcon(s.main?.className ?: s.familyName, size = 36.dp, grade = 1)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.familyName, color = BdoColors.onBg, fontWeight = FontWeight.SemiBold)
                                s.main?.let { Text("${it.className} · Lv ${it.level}", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint) }
                            }
                            if (!s.guild.isNullOrBlank()) Text("⟨${s.guild}⟩", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                        }
                    }
                }
            }
        }
    }
}

// ── Guilds ────────────────────────────────────────────────────────────────────
@Composable
private fun GuildsTab(api: BdoAlertsApi, onOpenGuild: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GuildSearchResult>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); searched = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        searched = true
        results = (api.guildSearchTyped(REGION_P, query.trim()) as? ApiResult.Success)?.data ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchField(query, { query = it; searched = false }, "Guild name (NA)") { }
        Spacer(Modifier.height(8.dp))
        when {
            query.trim().length < 2 -> CenterHint("Search a guild by name (NA). Only guilds looked up on BDO Alerts are indexed.")
            searched && results.isEmpty() -> CenterHint("No guild matching \"${query.trim()}\" is indexed yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(results, key = { it.guildName }) { g ->
                    BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenGuild(g.guildName) }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Monogram(text = g.guildName.take(2).uppercase(), grade = 2, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.guildName, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                                Text("Master ${g.guildMaster}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                            }
                            Text("${g.memberCount}", style = BdoType.num.copy(fontSize = 16.sp), color = BdoColors.goldHi)
                        }
                    }
                }
            }
        }
    }
}

// ── Player detail ─────────────────────────────────────────────────────────────
@Composable
private fun PlayerDetail(api: BdoAlertsApi, repo: FavoritesRepository, family: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ApiResult<PlayerProfile>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val favs by repo.favorites.collectAsState(initial = emptyList())
    val isFav = favs.any { it.type == FavoriteType.PLAYER && it.familyName.equals(family, true) }

    // The player endpoint scrapes the (bot-protected) official profile, so it's flaky —
    // retry a couple of times before showing an error, then remember the main class.
    LaunchedEffect(family, reload) {
        state = null
        var r = api.playerProfile(REGION_P, family)
        var tries = 0
        while (r !is ApiResult.Success && r !is ApiResult.HttpError && tries < 3) {
            kotlinx.coroutines.delay(500)
            r = api.playerProfile(REGION_P, family)
            tries++
        }
        // one extra retry even on a 5xx http error
        if (r is ApiResult.HttpError && r.code >= 500 && tries < 4) {
            kotlinx.coroutines.delay(500); r = api.playerProfile(REGION_P, family)
        }
        state = r
        if (r is ApiResult.Success) {
            val mc = r.data.characters.firstOrNull { it.isMain }?.className
                ?: r.data.characters.firstOrNull()?.className ?: ""
            repo.updateMainClass(REGION_P, family, mc)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader(family, onBack)
        when (val s = state) {
            null -> CenterHint("Loading $family…")
            is ApiResult.Success -> {
                val mainClass = s.data.characters.firstOrNull { it.isMain }?.className
                    ?: s.data.characters.firstOrNull()?.className ?: ""
                ProfileBody(s.data, isFav) {
                    scope.launch {
                        repo.toggle(
                            FavoriteType.PLAYER, title = s.data.familyName,
                            subtitle = (s.data.guild?.takeIf { it.isNotBlank() }?.let { "⟨$it⟩" } ?: mainClass),
                            region = REGION_P, familyName = s.data.familyName, mainClass = mainClass,
                        )
                    }
                }
            }
            is ApiResult.HttpError ->
                if (s.code == 404) CenterHint("No Family named \"$family\" on NA.")
                else RetryHint("Couldn't load $family (error ${s.code}).") { reload++ }
            else -> RetryHint("Couldn't load $family.") { reload++ }
        }
    }
}

@Composable
private fun RetryHint(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = BdoColors.onMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(
            onClick = onRetry,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BdoColors.gold, contentColor = BdoColors.onGold),
        ) { Text("Retry") }
    }
}

@Composable
private fun ProfileBody(p: PlayerProfile, isFav: Boolean, onToggleFav: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // [profile]
        item {
            BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, contentPadding = PaddingValues(16.dp)) {
                val mainClass = p.characters.firstOrNull { it.isMain }?.className ?: p.characters.firstOrNull()?.className ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClassIcon(mainClass.ifBlank { p.familyName }, size = 52.dp, grade = 3, glow = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.familyName, style = BdoType.display.copy(fontSize = 22.sp), color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!p.guild.isNullOrBlank()) Text("⟨${p.guild}⟩", style = MaterialTheme.typography.bodySmall, color = BdoColors.goldHi)
                        else if (p.familyCreated.isNotBlank()) Text("Since ${p.familyCreated}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    if (p.gearScore > 0) Column(horizontalAlignment = Alignment.End) {
                        Text(p.gearScore.toString(), style = BdoType.num.copy(fontSize = 26.sp), color = BdoColors.goldHi)
                        Text("GEAR SCORE", style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
                    }
                    IconButton(onClick = onToggleFav) {
                        Icon(if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorite", tint = if (isFav) BdoColors.goldHi else BdoColors.onFaint)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Contribution", p.contributionPoints.toString())
                    Stat("Energy", p.energy.toString())
                    Stat("Characters", p.characters.size.toString())
                    Stat("Life Skills", p.lifeSkills.count { it.isTrained }.toString())
                }
            }
        }
        // [lifeskills]
        val skills = p.lifeSkills.sortedByDescending { it.isTrained }
        if (skills.isNotEmpty()) {
            item { SectionLabel("Life skills", Modifier.padding(top = 6.dp)) }
            item {
                BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    skills.forEachIndexed { i, ls ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(ls.skill, Modifier.weight(1f), color = if (ls.isTrained) BdoColors.onBg else BdoColors.onFaint)
                            Text(ls.display, color = if (ls.isTrained) BdoColors.goldHi else BdoColors.onFaint, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        // [characters]
        if (p.characters.isNotEmpty()) {
            item { SectionLabel("Characters", Modifier.padding(top = 6.dp)) }
            items(p.characters, key = { it.name + it.className }) { c ->
                BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClassIcon(c.className, size = 38.dp, grade = if (c.isMain) 3 else 0, glow = c.isMain)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                                if (c.isMain) { Spacer(Modifier.width(6.dp)); Diamond(size = 5.dp, glow = true) }
                            }
                            Text("${c.className} · Lv ${c.level}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                        }
                    }
                }
            }
        }
    }
}

// ── Guild detail ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuildDetail(api: BdoAlertsApi, repo: FavoritesRepository, guildName: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ApiResult<GuildProfile>?>(null) }
    val favs by repo.favorites.collectAsState(initial = emptyList())
    val isFav = favs.any { it.type == FavoriteType.GUILD && it.familyName.equals(guildName, true) }
    LaunchedEffect(guildName) { state = null; state = api.guildProfile(REGION_P, guildName) }

    Column(Modifier.fillMaxSize()) {
        BackHeader(guildName, onBack)
        when (val s = state) {
            null -> CenterHint("Loading $guildName…")
            is ApiResult.Success -> {
                val g = s.data
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    item {
                        BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Monogram(text = g.guildName.take(2).uppercase(), grade = 3, size = 52.dp, glow = true)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.guildName, style = BdoType.display.copy(fontSize = 22.sp), color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Master · ${g.guildMaster}", style = MaterialTheme.typography.bodySmall, color = BdoColors.goldHi)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(g.memberCount.toString(), style = BdoType.num.copy(fontSize = 22.sp), color = BdoColors.goldHi)
                                    Text("MEMBERS", style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
                                }
                                IconButton(onClick = {
                                    scope.launch { repo.toggle(FavoriteType.GUILD, title = g.guildName, subtitle = "${g.memberCount} members", region = REGION_P, familyName = g.guildName) }
                                }) {
                                    Icon(if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorite", tint = if (isFav) BdoColors.goldHi else BdoColors.onFaint)
                                }
                            }
                        }
                    }
                    if (g.members.isNotEmpty()) {
                        item { SectionLabel("Roster", Modifier.padding(top = 6.dp)) }
                        item {
                            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    g.members.forEach { m ->
                                        Text(m, style = MaterialTheme.typography.labelMedium, color = if (m == g.guildMaster) BdoColors.goldHi else BdoColors.onMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> CenterHint("Couldn't load guild.")
        }
    }
}

// ── shared ────────────────────────────────────────────────────────────────────
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text(hint) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BdoColors.gold, cursorColor = BdoColors.gold, focusedLeadingIconColor = BdoColors.gold,
        ),
    )
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), Alignment.TopCenter) {
        Text(text, color = BdoColors.onFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = BdoType.num.copy(fontSize = 16.sp), color = BdoColors.onBg)
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
    }
}
