package com.gpowell.bdoboss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.api.ApiResult
import com.gpowell.bdoboss.data.api.BdoAlertsApi
import com.gpowell.bdoboss.data.api.Coupon
import com.gpowell.bdoboss.data.api.MaintenanceStatus
import com.gpowell.bdoboss.data.api.NewsItem
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoSubTabs
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.goldGlow

private const val REGION = "na"

@Composable
fun EventsScreen(onOpenSettings: () -> Unit, onOpenUrl: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Events & Coupons",
            blurb = "Live news, an event timeline, and redeemable coupon codes from the BDO Alerts service.",
            bullets = listOf("Official news & patch notes", "Active event timeline", "Coupon codes (tap to copy)"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BdoSubTabs(
            tabs = listOf("News", "Timeline", "Coupons", "Maintenance"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        when (tab) {
            0 -> NewsTab(api, onOpenUrl)
            1 -> TimelineTab(api, onOpenUrl)
            2 -> CouponsTab(api)
            else -> MaintenanceTab(api)
        }
    }
}

// ── Coupons ─────────────────────────────────────────────────────────────────
@Composable
private fun CouponsTab(api: BdoAlertsApi) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ApiResult<List<Coupon>>?>(null) }
    LaunchedEffect(reload) { state = null; state = api.coupons() }
    val clipboard = LocalClipboardManager.current

    ApiList(
        state = state,
        empty = "No active coupons right now.",
        onRetry = { reload++ },
    ) { coupons ->
        items(coupons, key = { it.code }) { c ->
            BdoCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.code, style = BdoType.num.copy(fontSize = 16.sp), color = BdoColors.goldHi, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(c.code)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = BdoColors.gold, modifier = Modifier.size(18.dp))
                    }
                }
                if (c.rewards.isNotBlank()) {
                    Text(c.rewards, style = MaterialTheme.typography.bodyMedium, color = BdoColors.onBg)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.platform.isNotBlank()) {
                        Text(c.platform, style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                        if (c.expires.isNotBlank()) { Spacer(Modifier.width(6.dp)); Diamond(size = 4.dp, color = BdoColors.onFaint); Spacer(Modifier.width(6.dp)) }
                    }
                    if (c.expires.isNotBlank()) {
                        Text("expires ${c.expires}", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                    }
                }
            }
        }
    }
}

// ── News ──────────────────────────────────────────────────────────────────────
private val NEWS_BOARDS = listOf(1 to "Notices", 2 to "Updates", 3 to "Events", 4 to "GM Notes", 5 to "Pearl Shop")

@Composable
private fun NewsTab(api: BdoAlertsApi, onOpenUrl: (String) -> Unit) {
    var board by rememberSaveable { mutableStateOf(2) } // Updates
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ApiResult<List<NewsItem>>?>(null) }
    LaunchedEffect(board, reload) { state = null; state = api.news(board, limit = 30) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NEWS_BOARDS.forEach { (id, label) ->
                BdoChip(label, active = board == id, onClick = { board = id })
            }
        }
        ApiList(state = state, empty = "No posts on this board.", onRetry = { reload++ }) { news ->
            items(news, key = { it.title + it.whenText }) { n ->
                BdoCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = if (n.href.isNotBlank()) { { onOpenUrl(n.href) } } else null,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = n.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(width = 84.dp, height = 48.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(n.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (n.whenText.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(n.whenText, style = MaterialTheme.typography.labelSmall, color = BdoColors.goldHi)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Timeline (event gantt) ────────────────────────────────────────────────────
// Built from the Events news board (board_type 3); each item's date_posted is either
// "N days left" (dated → a bar scaled by days remaining) or "Ongoing" (collapsible list).
private val EVENT_COLORS = listOf(
    androidx.compose.ui.graphics.Color(0xFF3B82F6), androidx.compose.ui.graphics.Color(0xFFF97316),
    androidx.compose.ui.graphics.Color(0xFFEF4444), androidx.compose.ui.graphics.Color(0xFFEC4899),
    androidx.compose.ui.graphics.Color(0xFF06B6D4), androidx.compose.ui.graphics.Color(0xFF8B5CF6),
    androidx.compose.ui.graphics.Color(0xFFEAB308), androidx.compose.ui.graphics.Color(0xFF14B8A6),
)

private val DAYS_LEFT = Regex("""(\d+)\s*days?\s*left""", RegexOption.IGNORE_CASE)
private fun daysLeftOf(text: String): Int? = DAYS_LEFT.find(text)?.groupValues?.get(1)?.toIntOrNull()

@Composable
private fun TimelineTab(api: BdoAlertsApi, onOpenUrl: (String) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ApiResult<List<NewsItem>>?>(null) }
    LaunchedEffect(reload) { state = null; state = api.news(3, limit = 50) }

    when (val s = state) {
        null -> LoadingLine()
        is ApiResult.Success -> {
            val dated = s.data.mapNotNull { n -> daysLeftOf(n.whenText)?.let { n to it } }.sortedBy { it.second }
            val ongoing = s.data.filter { daysLeftOf(it.whenText) == null }
            val maxDays = (dated.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            if (dated.isEmpty() && ongoing.isEmpty()) { CenterNote("No active events."); return }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (dated.isNotEmpty()) {
                    item { com.gpowell.bdoboss.ui.theme.SectionLabel("Ending soonest", Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)) }
                    itemsIndexed(dated) { i, (n, days) ->
                        EventBar(n, days, maxDays, EVENT_COLORS[i % EVENT_COLORS.size]) { onOpenUrl(n.href) }
                    }
                }
                if (ongoing.isNotEmpty()) {
                    item { com.gpowell.bdoboss.ui.theme.SectionLabel("Ongoing", Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) }
                    itemsIndexed(ongoing) { i, n ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenUrl(n.href) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(EVENT_COLORS[i % EVENT_COLORS.size]))
                            Spacer(Modifier.width(12.dp))
                            Text(n.title, Modifier.weight(1f), color = BdoColors.onBg, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("Ongoing", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                        }
                    }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.TopCenter) { ErrorLine(s) { reload++ } }
    }
}

@Composable
private fun EventBar(n: NewsItem, days: Int, maxDays: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    BdoCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onClick, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = n.imageUrl, contentDescription = null,
                    modifier = Modifier.size(width = 72.dp, height = 42.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(n.title, color = BdoColors.onBg, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                // duration bar: fill ∝ days remaining vs the longest active event
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(BdoColors.line)) {
                    Box(
                        Modifier.fillMaxWidth(fraction = (days.toFloat() / maxDays).coerceIn(0.04f, 1f))
                            .height(6.dp).clip(RoundedCornerShape(50)).background(color),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("${days}d", style = BdoType.num.copy(fontSize = 15.sp), color = BdoColors.goldHi)
        }
    }
}

// ── Maintenance ─────────────────────────────────────────────────────────────
@Composable
private fun MaintenanceTab(api: BdoAlertsApi) {
    var reload by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<ApiResult<MaintenanceStatus>?>(null) }
    LaunchedEffect(reload) { state = null; state = api.maintenanceStatus(REGION) }

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        when (val s = state) {
            null -> LoadingLine()
            is ApiResult.Success -> {
                val m = s.data
                val accent = if (m.active) BdoColors.down else BdoColors.up
                BdoCard(Modifier.fillMaxWidth(), facet = true, glow = true, contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Diamond(size = 8.dp, color = accent, glow = true)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (m.active) "Maintenance in progress" else "Servers online",
                            style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold,
                        )
                    }
                    if (m.countdown.isNotBlank()) {
                        Spacer(Modifier.height(10.dp)); Text(m.countdown, style = BdoType.num.copy(fontSize = 22.sp), color = BdoColors.goldHi)
                    }
                    if (m.message.isNotBlank()) {
                        Spacer(Modifier.height(8.dp)); Text(m.message, style = MaterialTheme.typography.bodyMedium, color = BdoColors.onMuted)
                    }
                    if (m.nextMaintenance.isNotBlank()) {
                        Spacer(Modifier.height(6.dp)); Text("Next: ${m.nextMaintenance}", style = MaterialTheme.typography.bodySmall, color = BdoColors.onFaint)
                    }
                }
            }
            else -> ErrorLine(s) { reload++ }
        }
    }
}

// ── Shared API list/loading/error scaffolding ─────────────────────────────────
@Composable
private fun <T> ApiList(
    state: ApiResult<List<T>>?,
    empty: String,
    onRetry: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.(List<T>) -> Unit,
) {
    when (state) {
        null -> LoadingLine()
        is ApiResult.Success ->
            if (state.data.isEmpty()) CenterNote(empty)
            else LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content(state.data) }
        else -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.TopCenter) { ErrorLine(state, onRetry) }
    }
}

@Composable private fun LoadingLine() {
    Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
        Text("Loading…", color = BdoColors.onFaint)
    }
}

@Composable private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), Alignment.TopCenter) {
        Text(text, color = BdoColors.onFaint, textAlign = TextAlign.Center)
    }
}

@Composable private fun ErrorLine(state: ApiResult<*>, onRetry: () -> Unit) {
    val msg = when (state) {
        is ApiResult.HttpError -> if (state.code == 403) "API key rejected — check it in Settings." else "Couldn't load (error ${state.code})."
        ApiResult.Offline -> "You're offline."
        else -> "Couldn't load."
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, color = BdoColors.onMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = BdoColors.gold, contentColor = BdoColors.onGold)) {
            Text("Retry")
        }
    }
}

/** The Vitrine "locked tab" state — diamond-framed padlock + what-you'll-get + unlock CTA. */
@Composable
internal fun LockedFeature(
    title: String,
    blurb: String,
    bullets: List<String>,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).goldGlow(RoundedCornerShape(16.dp), 14.dp).rotate(45f)
                .clip(RoundedCornerShape(16.dp)).background(BdoColors.surface1)
                .border(1.dp, BdoColors.goldLine, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = BdoColors.goldHi, modifier = Modifier.size(26.dp).rotate(-45f))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = BdoType.display.copy(fontSize = 24.sp), color = BdoColors.onBg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(blurb, style = MaterialTheme.typography.bodyMedium, color = BdoColors.onFaint, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bullets.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Diamond(size = 6.dp); Spacer(Modifier.width(10.dp))
                    Text(b, style = MaterialTheme.typography.bodyMedium, color = BdoColors.onMuted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = BdoColors.gold, contentColor = BdoColors.onGold)) {
            Text("Unlock with API key", fontWeight = FontWeight.SemiBold)
        }
    }
}
