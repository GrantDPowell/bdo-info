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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun EventsScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Events & Coupons",
            blurb = "Live patch notes, events, and redeemable coupon codes from the BDO Alerts service.",
            bullets = listOf("Active coupon codes (tap to copy)", "Official news & patch notes", "Maintenance & server status"),
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val api = remember { BdoAlertsApi(keyProvider = { repo.apiKey() }) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BdoSubTabs(
            tabs = listOf("Coupons", "News", "Maintenance"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        when (tab) {
            0 -> CouponsTab(api)
            1 -> NewsTab(api)
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
private fun NewsTab(api: BdoAlertsApi) {
    val ctx = LocalContext.current
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
                    onClick = if (n.href.isNotBlank()) {
                        { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.href))) } }
                    } else null,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Text(n.title, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                    if (n.whenText.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(n.whenText, style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                    }
                }
            }
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
