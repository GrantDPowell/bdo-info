package com.gpowell.bdoboss

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.gpowell.bdoboss.data.Schedule
import com.gpowell.bdoboss.data.ScheduleRepository
import com.gpowell.bdoboss.data.ScheduleUpdater
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.domain.SpawnCalculator
import com.gpowell.bdoboss.live.BossAlertsSocket
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.ui.AppSettingsScreen
import com.gpowell.bdoboss.ui.BossDetailSheet
import com.gpowell.bdoboss.ui.BossesScreen
import com.gpowell.bdoboss.ui.EventsScreen
import com.gpowell.bdoboss.ui.LiveHeader
import com.gpowell.bdoboss.ui.MarketScreen
import com.gpowell.bdoboss.ui.ProfileScreen
import com.gpowell.bdoboss.ui.hub.HubScreen
import com.gpowell.bdoboss.ui.theme.BdoBossTheme
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime

private data class TopTab(val label: String, val icon: ImageVector)

// material-icons-extended is required: ShoppingCart, Lock, and Visibility/VisibilityOff
// (used here and in the settings/locked screens) aren't in icons-core.
private val TOP_TABS = listOf(
    TopTab("Bosses", Icons.Filled.Notifications),
    TopTab("Market", Icons.Filled.ShoppingCart),
    TopTab("Events", Icons.Filled.Email),
    TopTab("Profile", Icons.Filled.Person),
    TopTab("Hub", Icons.Filled.Share),
)

class MainActivity : ComponentActivity() {

    companion object {
        // Over-the-air schedule updates: bump `version` in this file on GitHub and the
        // app picks it up on next launch — no app update needed.
        private const val SCHEDULE_URL =
            "https://raw.githubusercontent.com/GrantDPowell/bdo-info/master/app/src/main/assets/schedule_na.json"

        // Same OTA mechanism for the boss drop data: bump `version` in boss_info.json on GitHub.
        private const val BOSS_INFO_URL =
            "https://raw.githubusercontent.com/GrantDPowell/bdo-info/master/app/src/main/assets/boss_info.json"
    }

    private lateinit var liveSocket: BossAlertsSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        liveSocket = BossAlertsSocket(lifecycleScope)

        setContent {
            BdoBossTheme {
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
                var schedule by remember { mutableStateOf<Schedule?>(null) }
                var spawns by remember { mutableStateOf<List<Spawn>>(emptyList()) }
                var bossInfo by remember { mutableStateOf<BossInfo?>(null) }
                var selectedSpawn by remember { mutableStateOf<Spawn?>(null) }
                val live by liveSocket.state.collectAsState()

                LaunchedEffect(Unit) {
                    val loaded = withContext(Dispatchers.IO) {
                        ScheduleRepository(this@MainActivity).load()
                    }
                    schedule = loaded
                    spawns = SpawnCalculator.upcoming(loaded, Instant.now(), 40)
                    lifecycleScope.launch { AlarmScheduler.rearm(this@MainActivity) }

                    bossInfo = runCatching {
                        withContext(Dispatchers.IO) {
                            BossInfoRepository(this@MainActivity).load()
                        }
                    }.getOrNull()

                    if (ScheduleUpdater(this@MainActivity.applicationContext).checkForUpdate(SCHEDULE_URL)) {
                        val updated = withContext(Dispatchers.IO) {
                            ScheduleRepository(this@MainActivity).load()
                        }
                        schedule = updated
                        spawns = SpawnCalculator.upcoming(updated, Instant.now(), 40)
                        AlarmScheduler.rearm(this@MainActivity.applicationContext)
                    }

                    if (BossInfoUpdater(this@MainActivity.applicationContext).checkForUpdate(BOSS_INFO_URL)) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                BossInfoRepository(this@MainActivity).load()
                            }
                        }.getOrNull()?.let { bossInfo = it }
                    }
                }

                // Drift check: compare live boss next_spawn against local schedule
                LaunchedEffect(live.bosses) {
                    if (live.bosses.isEmpty() || spawns.isEmpty()) return@LaunchedEffect
                    for (liveBoss in live.bosses) {
                        val rawSpawn = liveBoss.nextSpawn ?: continue
                        val liveInstant = runCatching {
                            OffsetDateTime.parse(rawSpawn).toInstant()
                        }.getOrElse {
                            // next_spawn uses ISO-8601 with offset (e.g. -07:00) — not strict UTC.
                            // If unparseable just log the raw string.
                            Log.w("ScheduleDrift", "unparseable next_spawn for ${liveBoss.bossName}: $rawSpawn")
                            null
                        } ?: continue

                        // Find the nearest upcoming local spawn that includes this boss
                        val localMatch = spawns
                            .filter { liveBoss.bossName in it.bosses }
                            .minByOrNull { Math.abs(it.at.epochSecond - liveInstant.epochSecond) }
                            ?: continue

                        val diffSeconds = Math.abs(localMatch.at.epochSecond - liveInstant.epochSecond)
                        if (diffSeconds > 120) {
                            Log.w(
                                "ScheduleDrift",
                                "${liveBoss.bossName}: live=${rawSpawn} local=${localMatch.at} diff=${diffSeconds}s",
                            )
                        }
                    }
                }

                val backgroundBrush = remember {
                    Brush.verticalGradient(
                        0f to Color(0xFF0B0A08),
                        0.5f to Color(0xFF14110C),
                        1f to Color(0xFF0B0A08),
                    )
                }
                Box(Modifier.fillMaxSize().background(backgroundBrush)) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        topBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "BDO INFO",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BdoGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                )
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                        tint = BdoGold,
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(containerColor = Color(0xFF14110C)) {
                                val navItemColors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    indicatorColor = BdoGold,
                                    selectedTextColor = BdoGold,
                                )
                                TOP_TABS.forEachIndexed { i, t ->
                                    NavigationBarItem(
                                        selected = tab == i, onClick = { tab = i },
                                        icon = { Icon(t.icon, contentDescription = t.label) },
                                        label = { Text(t.label) },
                                        colors = navItemColors,
                                    )
                                }
                            }
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
                                        spawns = spawns,
                                        schedule = schedule,
                                        onSpawnClick = { selectedSpawn = it },
                                        headerContent = { LiveHeader(live) },
                                    )
                                    1 -> MarketScreen()
                                    2 -> EventsScreen(onOpenSettings = { showSettings = true })
                                    3 -> ProfileScreen(onOpenSettings = { showSettings = true })
                                    4 -> HubScreen()
                                }
                            }
                        }
                        selectedSpawn?.let { spawn ->
                            BossDetailSheet(spawn, bossInfo) { selectedSpawn = null }
                        }
                    }
                    if (showSettings) {
                        AppSettingsScreen(onBack = { showSettings = false })
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
