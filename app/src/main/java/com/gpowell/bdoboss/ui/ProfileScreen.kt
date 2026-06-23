package com.gpowell.bdoboss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
fun ProfileScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Adventurer Profile",
            blurb = "Pin your Family and look up any player or guild: gear, lifeskills, contribution & members — via BDO Alerts.",
            bullets = listOf("Pin your own profile for one-tap access", "Gear, lifeskills & characters", "Guild master & member roster"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BdoSubTabs(
            tabs = listOf("Adventurer", "Guild"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        when (tab) {
            0 -> AdventurerTab(api, repo)
            else -> GuildTab(api)
        }
    }
}

// ── Adventurer ────────────────────────────────────────────────────────────────
@Composable
private fun AdventurerTab(api: BdoAlertsApi, settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val myFamily by settings.myFamilyFlow.collectAsState(initial = "")
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlayerSearchResult>>(emptyList()) }
    var state by remember { mutableStateOf<ApiResult<PlayerProfile>?>(null) }

    // First open: if a profile is pinned and nothing's been searched, auto-load it.
    androidx.compose.runtime.LaunchedEffect(myFamily) {
        if (submitted.isBlank() && myFamily.isNotBlank()) submitted = myFamily
    }
    androidx.compose.runtime.LaunchedEffect(submitted) {
        if (submitted.isBlank()) { state = null; return@LaunchedEffect }
        state = null
        state = api.playerProfile(REGION_P, submitted)
    }
    // Debounced family-name autocomplete.
    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.trim().length < 2 || query.trim() == submitted) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        suggestions = (api.playerSearch(REGION_P, query.trim()) as? ApiResult.Success)?.data ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Family name (NA)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = query.trim(); suggestions = emptyList() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BdoColors.gold, cursorColor = BdoColors.gold, focusedLeadingIconColor = BdoColors.gold,
            ),
        )
        // autocomplete suggestions
        suggestions.take(6).forEach { sug ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    submitted = sug.familyName; query = sug.familyName; suggestions = emptyList()
                }.padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = BdoColors.onFaint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(10.dp))
                Text(sug.familyName, Modifier.weight(1f), color = BdoColors.onBg)
                if (!sug.guild.isNullOrBlank()) Text("⟨${sug.guild}⟩", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
            }
        }
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            null -> if (submitted.isBlank()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Diamond(size = 8.dp, glow = true)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (myFamily.isBlank()) "Search a Family, then ☆ to pin it as yours." else "Loading $myFamily…",
                            color = BdoColors.onFaint,
                        )
                    }
                }
            } else Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("Loading…", color = BdoColors.onFaint) }
            is ApiResult.Success -> ProfileBody(
                p = s.data,
                isMine = s.data.familyName.equals(myFamily, true),
                onToggleMine = {
                    scope.launch {
                        settings.setMyFamily(if (s.data.familyName.equals(myFamily, true)) "" else s.data.familyName)
                    }
                },
            )
            is ApiResult.HttpError -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) {
                Text(if (s.code == 404) "No Family named \"$submitted\" on NA." else "Couldn't load (error ${s.code}).", color = BdoColors.onMuted)
            }
            ApiResult.Offline -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("You're offline.", color = BdoColors.onMuted) }
            ApiResult.NoKey -> Unit
        }
    }
}

@Composable
private fun ProfileBody(p: PlayerProfile, isMine: Boolean, onToggleMine: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(text = p.familyName.take(2).uppercase(), grade = 3, size = 52.dp, glow = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.familyName, style = BdoType.display.copy(fontSize = 22.sp), color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!p.guild.isNullOrBlank()) {
                            Text("⟨${p.guild}⟩", style = MaterialTheme.typography.bodySmall, color = BdoColors.goldHi)
                        } else if (p.familyCreated.isNotBlank()) {
                            Text("Since ${p.familyCreated}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                        }
                    }
                    IconButton(onClick = onToggleMine) {
                        Icon(
                            if (isMine) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isMine) "Unpin my profile" else "Pin as my profile",
                            tint = if (isMine) BdoColors.goldHi else BdoColors.onFaint,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Gear Score", p.gearScore.toString())
                    Stat("Contribution", p.contributionPoints.toString())
                    Stat("Energy", p.energy.toString())
                    Stat("Characters", p.characters.size.toString())
                }
            }
        }

        if (p.characters.isNotEmpty()) {
            item { SectionLabel("Characters", Modifier.padding(top = 6.dp)) }
            items(p.characters, key = { it.name + it.className }) { c ->
                BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(text = c.className.take(2).uppercase(), grade = if (c.isMain) 3 else 0, size = 38.dp)
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

        val skills = p.lifeSkills.filter { it.isTrained }.ifEmpty { p.lifeSkills }
        if (skills.isNotEmpty()) {
            item { SectionLabel("Life skills", Modifier.padding(top = 6.dp)) }
            item {
                BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    skills.forEachIndexed { i, ls ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(ls.skill, Modifier.weight(1f), color = BdoColors.onMuted)
                            Text(ls.display, color = BdoColors.goldHi, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = BdoType.num.copy(fontSize = 16.sp), color = BdoColors.onBg)
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
    }
}

// ── Guild ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuildTab(api: BdoAlertsApi) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GuildSearchResult>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ApiResult<GuildProfile>?>(null) }

    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        results = (api.guildSearchTyped(REGION_P, query.trim()) as? ApiResult.Success)?.data ?: emptyList()
    }
    androidx.compose.runtime.LaunchedEffect(selected) {
        val g = selected ?: return@LaunchedEffect
        profile = null
        profile = api.guildProfile(REGION_P, g)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; selected = null; profile = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Guild name (NA)") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BdoColors.gold, cursorColor = BdoColors.gold, focusedLeadingIconColor = BdoColors.gold,
            ),
        )
        Spacer(Modifier.height(8.dp))

        if (selected == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(results, key = { it.guildName }) { g ->
                    BdoCard(Modifier.fillMaxWidth(), onClick = { selected = g.guildName }, contentPadding = PaddingValues(14.dp)) {
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
        } else when (val pr = profile) {
            null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("Loading…", color = BdoColors.onFaint) }
            is ApiResult.Success -> {
                val g = pr.data
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
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
                            }
                        }
                    }
                    if (g.members.isNotEmpty()) {
                        item { SectionLabel("Roster", Modifier.padding(top = 6.dp)) }
                        item {
                            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    g.members.forEach { m ->
                                        Text(
                                            m,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (m == g.guildMaster) BdoColors.goldHi else BdoColors.onMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("Couldn't load guild.", color = BdoColors.onMuted) }
        }
    }
}
