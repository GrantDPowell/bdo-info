package com.gpowell.bdoboss

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gpowell.bdoboss.data.BossInfo
import com.gpowell.bdoboss.data.BossInfoRepository
import com.gpowell.bdoboss.data.BossInfoUpdater
import com.gpowell.bdoboss.data.FavoritesRepository
import com.gpowell.bdoboss.data.LiveSpawnCache
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.market.ItemIndexRepository
import com.gpowell.bdoboss.data.market.MarketRepository
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.live.BossAlertsSocket
import com.gpowell.bdoboss.live.WsBoss
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.ui.AppSettingsScreen
import com.gpowell.bdoboss.ui.BossDetailSheet
import com.gpowell.bdoboss.ui.CreditsScreen
import com.gpowell.bdoboss.ui.BossesScreen
import com.gpowell.bdoboss.ui.EventsScreen
import com.gpowell.bdoboss.ui.LiveHeader
import com.gpowell.bdoboss.ui.ProfileScreen
import com.gpowell.bdoboss.ui.hub.HubScreen
import com.gpowell.bdoboss.ui.market.MarketScreen
import com.gpowell.bdoboss.ui.theme.BdoBackground
import com.gpowell.bdoboss.ui.theme.BdoBossTheme
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoType
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.LocalEffectsEnabled
import com.gpowell.bdoboss.ui.theme.goldGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime

private data class TopTab(val label: String, val icon: ImageVector)

/**
 * Build the displayed upcoming-spawn list from the LIVE bdoalerts feed (authoritative &
 * current). Pearl Abyss revises the world-boss schedule periodically, so the bundled
 * `schedule_na.json` can drift; when the socket is connected we show its real next-spawns
 * (merging co-spawns per instant). The local schedule still drives offline alarms.
 */
private fun liveToSpawns(bosses: List<WsBoss>): List<Spawn> {
    val byInstant = sortedMapOf<Instant, MutableList<String>>()
    for (b in bosses) {
        val raw = b.nextSpawn ?: continue
        val at = runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() ?: continue
        byInstant.getOrPut(at) { mutableListOf() }.add(b.bossName)
    }
    return byInstant.entries.map { Spawn(it.key, it.value.toList()) }
}

// material-icons-extended is required: these outlined glyphs (and Lock/Visibility used in
// the settings/locked screens) aren't in icons-core.
private val TOP_TABS = listOf(
    TopTab("Bosses", Icons.Outlined.Timer),
    TopTab("Market", Icons.Outlined.ShowChart),
    TopTab("Events", Icons.Outlined.CalendarMonth),
    TopTab("Profile", Icons.Outlined.Person),
    TopTab("Hub", Icons.Outlined.Public),
)

class MainActivity : ComponentActivity() {

    companion object {
        // OTA mechanism for the boss drop data: bump `version` in boss_info.json on GitHub.
        private const val BOSS_INFO_URL =
            "https://raw.githubusercontent.com/GrantDPowell/bdo-info/master/app/src/main/assets/boss_info.json"
    }

    private lateinit var liveSocket: BossAlertsSocket

    // Shared so the in-memory price cache (30-min TTL) survives sheet open/close.
    private val marketRepo = MarketRepository()

    // Shared so the parsed ~62k-entry index loads once per process.
    private val itemIndexRepo by lazy { ItemIndexRepository(applicationContext) }

    // DataStore behind it is a process-wide singleton; instance is cheap.
    private val favoritesRepo by lazy { FavoritesRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        liveSocket = BossAlertsSocket(lifecycleScope)

        setContent {
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val effectsEnabled by settingsRepo.effectsEnabledFlow.collectAsState(initial = true)
            val eclipseTheme by settingsRepo.eclipseThemeFlow.collectAsState(initial = false)
            BdoBossTheme(eclipse = eclipseTheme) {
              CompositionLocalProvider(LocalEffectsEnabled provides effectsEnabled) {
                val notifPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result reflected by AlertsScreen banner; nothing to do here */ }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                var tab by remember { mutableIntStateOf(0) }
                var showSettings by remember { mutableStateOf(false) }
                var showCredits by remember { mutableStateOf(false) }
                // Cross-tab nav: Hub ITEM favorites jump into Market item detail.
                // MarketScreen consumes the request and clears it.
                var marketDetailItemId by remember { mutableStateOf<Int?>(null) }
                // Cross-tab nav: Events links open in the Hub's in-app browser (so they
                // join the shared session + favorites). HubScreen consumes & clears it, and
                // when that browser closes we return to whichever tab opened it.
                var pendingBrowserUrl by remember { mutableStateOf<String?>(null) }
                var browserReturnTab by remember { mutableIntStateOf(4) }
                // Hub favorite (player/guild) tap → jump to Profile and open that detail.
                var pendingProfilePlayer by remember { mutableStateOf<String?>(null) }
                var pendingProfileGuild by remember { mutableStateOf<String?>(null) }
                // Single source of truth: the latest LIVE spawns (cached for offline/boot).
                var liveSpawns by remember { mutableStateOf<List<Spawn>>(emptyList()) }
                var lastArmedSig by remember { mutableStateOf("") }
                var bossInfo by remember { mutableStateOf<BossInfo?>(null) }
                var selectedSpawn by remember { mutableStateOf<Spawn?>(null) }
                val live by liveSocket.state.collectAsState()

                LaunchedEffect(Unit) {
                    // First-run: pre-favorite the default player (OkimaSha).
                    withContext(Dispatchers.IO) { favoritesRepo.seedDefaultsOnce() }
                    // Seed from the last-known live spawns (so the UI + alarms have data
                    // before the socket reconnects). The live feed refreshes these below.
                    liveSpawns = withContext(Dispatchers.IO) { LiveSpawnCache.load(this@MainActivity) }
                    lifecycleScope.launch { AlarmScheduler.rearm(this@MainActivity) }

                    bossInfo = runCatching {
                        withContext(Dispatchers.IO) {
                            BossInfoRepository(this@MainActivity).load()
                        }
                    }.getOrNull()

                    // Boss drop data is still OTA-updatable (it's reference data, not a schedule).
                    if (BossInfoUpdater(this@MainActivity.applicationContext).checkForUpdate(BOSS_INFO_URL)) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                BossInfoRepository(this@MainActivity).load()
                            }
                        }.getOrNull()?.let { bossInfo = it }
                    }
                }

                // The live feed is the single source of truth. Snapshot it, and when the
                // set of upcoming spawns actually changes, persist it + re-arm alarms so
                // notifications always track the real current schedule (no static data).
                LaunchedEffect(live.bosses) {
                    if (live.bosses.isEmpty()) return@LaunchedEffect
                    val fresh = liveToSpawns(live.bosses)
                    if (fresh.isEmpty()) return@LaunchedEffect
                    val sig = fresh.joinToString(";") { "${it.at.epochSecond}:${it.bosses.sorted()}" }
                    if (sig == lastArmedSig) return@LaunchedEffect
                    lastArmedSig = sig
                    liveSpawns = fresh
                    withContext(Dispatchers.IO) { LiveSpawnCache.save(this@MainActivity, fresh) }
                    lifecycleScope.launch { AlarmScheduler.rearm(this@MainActivity.applicationContext) }
                }

                // Back navigation (so the back gesture never just drops out of the app):
                // close the boss sheet first, otherwise fall back to the Bosses tab. Inner
                // screens (Settings/Credits overlays, browser, Profile/Codex detail) register
                // their own BackHandlers deeper in the tree and take priority over these.
                BackHandler(enabled = selectedSpawn != null) { selectedSpawn = null }
                BackHandler(enabled = selectedSpawn == null && !showSettings && !showCredits && tab != 0) { tab = 0 }

                Box(Modifier.fillMaxSize().background(BdoBackground)) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bottomBar = {
                            BdoBottomBar(
                                tabs = TOP_TABS,
                                selected = tab,
                                onSelect = { tab = it },
                            )
                        },
                    ) { pad ->
                        Box(Modifier.fillMaxSize().padding(pad)) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    val dir = if (targetState > initialState) 1 else -1
                                    (slideInHorizontally { it / 8 * dir } + fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally { -it / 8 * dir } + fadeOut(tween(160)))
                                },
                                label = "tab",
                            ) { t ->
                                when (t) {
                                    0 -> BossesScreen(
                                        spawns = liveSpawns,
                                        onSpawnClick = { selectedSpawn = it },
                                        onOpenSettings = { showSettings = true },
                                        headerContent = { LiveHeader(live) },
                                    )
                                    1 -> MarketScreen(
                                        market = marketRepo,
                                        favoritesRepo = favoritesRepo,
                                        itemIndex = itemIndexRepo,
                                        externalDetailItemId = marketDetailItemId,
                                        onExternalDetailConsumed = { marketDetailItemId = null },
                                    )
                                    2 -> EventsScreen(
                                        onOpenSettings = { showSettings = true },
                                        onOpenUrl = { pendingBrowserUrl = it; browserReturnTab = 2; tab = 4 },
                                    )
                                    3 -> ProfileScreen(
                                        onOpenSettings = { showSettings = true },
                                        externalPlayer = pendingProfilePlayer,
                                        externalGuild = pendingProfileGuild,
                                        onExternalConsumed = { pendingProfilePlayer = null; pendingProfileGuild = null },
                                    )
                                    4 -> HubScreen(
                                        onOpenItem = { itemId ->
                                            marketDetailItemId = itemId
                                            tab = 1
                                        },
                                        onOpenSettings = { showSettings = true },
                                        onOpenCredits = { showCredits = true },
                                        onOpenPlayer = { pendingProfilePlayer = it; tab = 3 },
                                        onOpenGuild = { pendingProfileGuild = it; tab = 3 },
                                        externalUrl = pendingBrowserUrl,
                                        onExternalUrlConsumed = { pendingBrowserUrl = null },
                                        onExternalBrowserClosed = { tab = browserReturnTab },
                                    )
                                }
                            }
                        }
                        selectedSpawn?.let { spawn ->
                            BossDetailSheet(spawn, bossInfo, marketRepo) { selectedSpawn = null }
                        }
                    }
                    if (showSettings) {
                        AppSettingsScreen(onBack = { showSettings = false })
                    }
                    if (showCredits) {
                        CreditsScreen(onBack = { showCredits = false })
                    }
                }
              }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        liveSocket.connect()
    }

    override fun onStop() {
        super.onStop()
        liveSocket.disconnect()
    }
}

/**
 * Custom 5-item bottom nav. Selected = goldHi icon with a glowing diamond indicator above;
 * unselected = onFaint. No M3 selection pill — the diamond + glow is the indicator.
 */
@Composable
private fun BdoBottomBar(
    tabs: List<TopTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BdoColors.bg1)
            .padding(top = 1.dp)
            .background(Color.Transparent),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BdoColors.line))
        Row(
            Modifier
                .fillMaxWidth()
                .background(BdoColors.bg1)
                .navigationBarsPadding()
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (on) Diamond(size = 5.dp, glow = true) else Spacer(Modifier.height(5.dp))
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = if (on) BdoColors.goldHi else BdoColors.onFaint,
                        modifier = Modifier
                            .size(23.dp)
                            .then(if (on) Modifier.goldGlow(RoundedCornerShape(50), 8.dp) else Modifier),
                    )
                    Text(
                        t.label.uppercase(),
                        style = BdoType.overline.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                        color = if (on) BdoColors.goldHi else BdoColors.onFaint,
                    )
                }
            }
        }
    }
}
