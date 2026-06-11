package com.gpowell.bdoboss

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.gpowell.bdoboss.data.Schedule
import com.gpowell.bdoboss.data.ScheduleRepository
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.domain.SpawnCalculator
import com.gpowell.bdoboss.live.BossAlertsSocket
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.ui.LiveHeader
import com.gpowell.bdoboss.ui.ScheduleScreen
import com.gpowell.bdoboss.ui.SettingsScreen
import com.gpowell.bdoboss.ui.TimersScreen
import com.gpowell.bdoboss.ui.theme.BdoBossTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class MainActivity : ComponentActivity() {

    private lateinit var liveSocket: BossAlertsSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        liveSocket = BossAlertsSocket(lifecycleScope)

        setContent {
            BdoBossTheme {
                var tab by remember { mutableIntStateOf(0) }
                var schedule by remember { mutableStateOf<Schedule?>(null) }
                var spawns by remember { mutableStateOf<List<Spawn>>(emptyList()) }
                val live by liveSocket.state.collectAsState()

                LaunchedEffect(Unit) {
                    val loaded = withContext(Dispatchers.IO) {
                        ScheduleRepository(this@MainActivity).load()
                    }
                    schedule = loaded
                    spawns = SpawnCalculator.upcoming(loaded, Instant.now(), 40)
                    lifecycleScope.launch { AlarmScheduler.rearm(this@MainActivity) }
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

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Notifications, contentDescription = "Timers") },
                                label = { Text("Timers") },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.DateRange, contentDescription = "Schedule") },
                                label = { Text("Schedule") },
                            )
                            NavigationBarItem(
                                selected = tab == 2, onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Alerts") },
                                label = { Text("Alerts") },
                            )
                        }
                    },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        when (tab) {
                            0 -> TimersScreen(spawns, headerContent = { LiveHeader(live) })
                            1 -> schedule?.let { ScheduleScreen(it) }
                            2 -> SettingsScreen()
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
