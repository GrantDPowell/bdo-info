package com.gpowell.bdoboss.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.data.api.PlayerProfile
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.Monogram
import com.gpowell.bdoboss.ui.theme.SectionLabel

private const val REGION_P = "na"

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Adventurer Profile",
            blurb = "Look up any Family or character: gear, lifeskills, contribution & guild — via BDO Alerts.",
            bullets = listOf("Gearscore & class breakdown", "Lifeskill levels", "Contribution, energy & guild"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ApiResult<PlayerProfile>?>(null) }

    androidx.compose.runtime.LaunchedEffect(submitted, reload) {
        if (submitted.isBlank()) { state = null; return@LaunchedEffect }
        state = null
        state = api.playerProfile(REGION_P, submitted)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Family name (NA)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BdoColors.gold,
                cursorColor = BdoColors.gold,
                focusedLeadingIconColor = BdoColors.gold,
            ),
        )
        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            null ->
                if (submitted.isBlank()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Search a Family name to view their profile.", color = BdoColors.onFaint)
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("Loading…", color = BdoColors.onFaint) }
                }
            is ApiResult.Success -> ProfileBody(s.data)
            is ApiResult.HttpError ->
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) {
                    Text(
                        if (s.code == 404) "No Family named \"$submitted\" on NA." else "Couldn't load (error ${s.code}).",
                        color = BdoColors.onMuted,
                    )
                }
            ApiResult.Offline ->
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.TopCenter) { Text("You're offline.", color = BdoColors.onMuted) }
            ApiResult.NoKey -> Unit
        }
    }
}

@Composable
private fun ProfileBody(p: PlayerProfile) {
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
                    if (p.gearScore > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p.gearScore.toString(), style = BdoType.num.copy(fontSize = 22.sp), color = BdoColors.goldHi)
                            Text("GS", style = BdoType.overline.copy(fontSize = 9.sp), color = BdoColors.onFaint)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                                if (c.isMain) {
                                    Spacer(Modifier.width(6.dp)); Diamond(size = 5.dp, glow = true)
                                }
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
        Text(label.uppercase(), style = BdoType.overline.copy(fontSize = 8.5.sp), color = BdoColors.onFaint)
    }
}
