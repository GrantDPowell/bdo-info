package com.gpowell.bdoboss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.Monogram
import com.gpowell.bdoboss.ui.theme.SectionLabel

private const val REGION_C = "na"

private enum class CodexFeature(val title: String, val sub: String, val glyph: String) {
    LEADERBOARDS("Leaderboards", "Top gear score & life skills", "LB"),
    GRINDSPOTS("Grind Spots", "Silver/hr, AP/DP & caps", "GS"),
    CRON("Cron Costs", "Cost per enhancement level", "CR"),
    LIGHTSTONES("Lightstone Sets", "Combat set builder", "LS"),
    SKILLS("Skills", "Full skill list per class", "SK"),
    TIERLISTS("Tier Lists", "Community class rankings", "TL"),
    GUIDES("Guides", "Community class guides", "GU"),
    HOT("Hot Market", "Most-traded items live", "HM"),
    STREAMERS("Streamers", "Live BDO on Twitch", "TW"),
}

@Composable
fun CodexScreen(onOpenSettings: () -> Unit, onOpenUrl: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Codex",
            blurb = "Leaderboards, grind spots, cron costs, lightstones, skills, tier lists, guides & streamers — powered by BDO Alerts.",
            bullets = listOf("Top players & grind spot profits", "Cron-cost & lightstone references", "Class skills, guides & live streams"),
            onOpenSettings = onOpenSettings,
        )
        return
    }
    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var feature by rememberSaveable { mutableStateOf<CodexFeature?>(null) }

    when (val f = feature) {
        null -> CodexLauncher(onPick = { feature = it })
        else -> Column(Modifier.fillMaxSize()) {
            CodexHeader(f.title) { feature = null }
            when (f) {
                CodexFeature.LEADERBOARDS -> LeaderboardsScreen(api)
                CodexFeature.GRINDSPOTS -> GrindSpotsScreen(api)
                CodexFeature.CRON -> CronScreen(api)
                CodexFeature.LIGHTSTONES -> LightstonesScreen(api)
                CodexFeature.SKILLS -> SkillsScreen(api)
                CodexFeature.TIERLISTS -> TierListsScreen(api, onOpenUrl)
                CodexFeature.GUIDES -> GuidesScreen(api, onOpenUrl)
                CodexFeature.HOT -> HotMarketScreen(api)
                CodexFeature.STREAMERS -> StreamersScreen(api, onOpenUrl)
            }
        }
    }
}

@Composable
private fun CodexLauncher(onPick: (CodexFeature) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Diamond(size = 10.dp, glow = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("CODEX", style = BdoType.display.copy(fontSize = 20.sp), color = BdoColors.onBg)
                    Text("Live reference · NA", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                }
            }
        }
        items(CodexFeature.entries) { f ->
            BdoCard(Modifier.fillMaxWidth(), onClick = { onPick(f) }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(text = f.glyph, grade = 2, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        Text(f.sub, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BdoColors.onFaint)
                }
            }
        }
    }
}

@Composable
private fun CodexHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BdoColors.goldHi) }
        Text(title, style = BdoType.display.copy(fontSize = 19.sp), color = BdoColors.onBg)
    }
}

// ── shared helpers ────────────────────────────────────────────────────────────
@Composable
private fun Loading() = Box(Modifier.fillMaxSize().padding(28.dp), Alignment.TopCenter) { Text("Loading…", color = BdoColors.onFaint) }

private fun silverShort(n: Long): String = when {
    n >= 1_000_000_000L -> "%.2fB".format(n / 1_000_000_000.0)
    n >= 1_000_000L -> "%.0fM".format(n / 1_000_000.0)
    n >= 1_000L -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}

// ── Leaderboards ──────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardsScreen(api: BdoAlertsApi) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0 = gear, 1 = lifeskills
    var gear by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.GearLeader>?>(null) }
    var life by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.LifeLeader>?>(null) }
    LaunchedEffect(Unit) {
        (api.leaderboardGearScore() as? ApiResult.Success)?.let { gear = it.data.leaderboard }
        (api.leaderboardLifeSkills() as? ApiResult.Success)?.let { life = it.data.leaderboard }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BdoChip("Gear Score", active = mode == 0, onClick = { mode = 0 })
            BdoChip("Life Skills", active = mode == 1, onClick = { mode = 1 })
        }
        if (mode == 0) {
            val g = gear
            if (g == null) Loading() else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(g, key = { it.rank }) { p ->
                    BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${p.rank}", style = BdoType.num.copy(fontSize = 15.sp), color = BdoColors.gold, modifier = Modifier.width(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.familyName, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                                Text("${p.mainClass} · ${p.region.uppercase()}${if (!p.guildName.isNullOrBlank()) " · ⟨${p.guildName}⟩" else ""}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(p.gearScore.toString(), style = BdoType.num.copy(fontSize = 18.sp), color = BdoColors.goldHi)
                        }
                    }
                }
            }
        } else {
            val l = life
            if (l == null) Loading() else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(l, key = { it.skillName }) { p ->
                    BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.skillName, fontWeight = FontWeight.SemiBold, color = BdoColors.goldHi)
                                Text("${p.familyName} · ${p.region.uppercase()}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                            }
                            Text(p.skillRank, style = BdoType.num.copy(fontSize = 14.sp), color = BdoColors.onBg)
                        }
                    }
                }
            }
        }
    }
}

// ── Grind Spots ───────────────────────────────────────────────────────────────
@Composable
private fun GrindSpotsScreen(api: BdoAlertsApi) {
    var spots by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.GrindSpot>?>(null) }
    LaunchedEffect(Unit) { (api.bestGrindspots() as? ApiResult.Success)?.let { spots = it.data.grindSpots } }
    val s = spots
    if (s == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s, key = { it.spotKey() }) { g ->
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${g.rank}", style = BdoType.num.copy(fontSize = 14.sp), color = BdoColors.gold, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        Text("AP ${g.apReq} · DP ${g.dpReq} · cap ${g.capAp}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    Text(g.silverPerHour, style = BdoType.num.copy(fontSize = 14.sp), color = BdoColors.goldHi)
                }
            }
        }
    }
}

private fun com.gpowell.bdoboss.data.api.GrindSpot.spotKey() = "$rank-$name"

// ── Cron Costs ────────────────────────────────────────────────────────────────
@Composable
private fun CronScreen(api: BdoAlertsApi) {
    var data by remember { mutableStateOf<com.gpowell.bdoboss.data.api.CronCostsResp?>(null) }
    var type by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        (api.cronCosts() as? ApiResult.Success)?.let { data = it.data; if (type.isBlank()) type = it.data.equipmentTypes.firstOrNull() ?: "" }
    }
    val d = data
    if (d == null) { Loading(); return }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.equipmentTypes.forEach { t -> BdoChip(t, active = t == type, onClick = { type = t }) }
        }
        val items = d.data[type].orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.name }) { item ->
                BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.imageUrl.isNotBlank()) {
                            AsyncImage(item.imageUrl, null, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("PRI", "DUO", "TRI", "TET", "PEN").forEach { lvl ->
                            val c = item.cronCosts[lvl]
                            if (c != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.toString(), style = BdoType.num.copy(fontSize = 14.sp), color = BdoColors.goldHi)
                                Text(lvl, style = BdoType.overline.copy(fontSize = 8.sp), color = BdoColors.onFaint)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Lightstones ───────────────────────────────────────────────────────────────
@Composable
private fun LightstonesScreen(api: BdoAlertsApi) {
    var sets by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.LightstoneSet>?>(null) }
    LaunchedEffect(Unit) { (api.lightstoneData() as? ApiResult.Success)?.let { sets = it.data.combatSets } }
    val s = sets
    if (s == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s, key = { it.name }) { set ->
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Text(set.name, fontWeight = FontWeight.SemiBold, color = BdoColors.goldHi)
                Spacer(Modifier.height(6.dp))
                set.requirements.forEach { req ->
                    Text("• ${req.stones.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onMuted)
                }
                set.bonusTiers.lastOrNull()?.let { tier ->
                    Spacer(Modifier.height(6.dp))
                    Text(tier.bonuses.entries.joinToString("  ") { "${it.key} ${it.value}" }, style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                }
            }
        }
    }
}

// ── Skills ────────────────────────────────────────────────────────────────────
private val BDO_CLASSES = listOf(
    "Warrior", "Ranger", "Sorceress", "Berserker", "Tamer", "Musa", "Maehwa", "Valkyrie",
    "Kunoichi", "Ninja", "Wizard", "Witch", "Dark Knight", "Striker", "Mystic", "Lahn",
    "Archer", "Shai", "Guardian", "Hashashin", "Nova", "Sage", "Corsair", "Drakania",
    "Woosa", "Maegu", "Scholar", "Dosa", "Deadeye", "Wukong",
)

@Composable
private fun SkillsScreen(api: BdoAlertsApi) {
    var cls by rememberSaveable { mutableStateOf("Warrior") }
    var skills by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.GameSkill>?>(null) }
    LaunchedEffect(cls) { skills = null; (api.classSkills(cls) as? ApiResult.Success)?.let { skills = it.data.skills } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BDO_CLASSES.forEach { c -> BdoChip(c, active = c == cls, onClick = { cls = c }) }
        }
        val s = skills
        if (s == null) Loading() else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s, key = { it.id }) { sk ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sk.iconUrl.isNotBlank()) {
                        AsyncImage(sk.iconUrl, null, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(sk.name, color = BdoColors.onBg)
                }
            }
        }
    }
}

// ── Tier Lists ────────────────────────────────────────────────────────────────
@Composable
private fun TierListsScreen(api: BdoAlertsApi, onOpenUrl: (String) -> Unit) {
    var lists by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.TierList>?>(null) }
    LaunchedEffect(Unit) { (api.tierLists() as? ApiResult.Success)?.let { lists = it.data.tierLists } }
    val l = lists
    if (l == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(l, key = { it.id }) { t ->
            BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenUrl("https://bdoalerts.net/tier-list/${t.shareCode}") }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title.ifBlank { "Tier list" }, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        Text("by ${t.username} · ${t.tierType.uppercase()} · ${t.views} views", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BdoColors.onFaint)
                }
            }
        }
    }
}

// ── Guides ────────────────────────────────────────────────────────────────────
@Composable
private fun GuidesScreen(api: BdoAlertsApi, onOpenUrl: (String) -> Unit) {
    var guides by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.Guide>?>(null) }
    LaunchedEffect(Unit) { (api.guides() as? ApiResult.Success)?.let { guides = it.data.guides } }
    val g = guides
    if (g == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(g, key = { it.id }) { gd ->
            BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenUrl("https://bdoalerts.net/guides/${gd.id}") }, contentPadding = PaddingValues(14.dp)) {
                Text(gd.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                Spacer(Modifier.height(2.dp))
                Text("${gd.className.replaceFirstChar { it.uppercase() }} · ${gd.guideType} · by ${gd.author}", style = MaterialTheme.typography.bodySmall, color = BdoColors.goldHi)
                if (gd.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(gd.description, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ── Hot Market ────────────────────────────────────────────────────────────────
@Composable
private fun HotMarketScreen(api: BdoAlertsApi) {
    var items by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.HotItem>?>(null) }
    LaunchedEffect(Unit) { (api.marketHotTyped(REGION_C, limit = 50) as? ApiResult.Success)?.let { items = it.data.items } }
    val it = items
    if (it == null) { Loading(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(it, key = { i -> "${i.itemId}-${i.subKey}" }) { item ->
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (arrow, tint) = when (item.direction.lowercase()) {
                        "up" -> "▲" to BdoColors.up
                        "down" -> "▼" to BdoColors.down
                        else -> "·" to BdoColors.onFaint
                    }
                    Text(arrow, color = tint, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${silverShort(item.totalTrades)} trades", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                    Text(silverShort(item.price), style = BdoType.num.copy(fontSize = 15.sp), color = BdoColors.goldHi)
                }
            }
        }
    }
}

// ── Streamers ─────────────────────────────────────────────────────────────────
@Composable
private fun StreamersScreen(api: BdoAlertsApi, onOpenUrl: (String) -> Unit) {
    var all by remember { mutableStateOf<List<com.gpowell.bdoboss.data.api.Streamer>?>(null) }
    LaunchedEffect(Unit) { (api.streamers() as? ApiResult.Success)?.let { all = it.data.streamers } }
    val s = all
    if (s == null) { Loading(); return }
    val sorted = s.sortedByDescending { it.isLive }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sorted.none { it.isLive }) item { SectionLabel("Nobody live right now") }
        items(sorted, key = { it.username }) { st ->
            BdoCard(Modifier.fillMaxWidth(), onClick = { onOpenUrl("https://twitch.tv/${st.username}") }, contentPadding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st.avatarUrl.isNotBlank()) {
                        AsyncImage(st.avatarUrl, null, Modifier.size(38.dp).clip(RoundedCornerShape(50)))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(st.displayName, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        if (st.isLive && !st.streamTitle.isNullOrBlank()) {
                            Text(st.streamTitle!!, style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (st.isLive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("● LIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(st.viewerCount.toString(), style = BdoType.num.copy(fontSize = 13.sp), color = BdoColors.goldHi)
                        }
                    } else {
                        Text("offline", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                    }
                }
            }
        }
    }
}
