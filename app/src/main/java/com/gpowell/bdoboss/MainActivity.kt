package com.gpowell.bdoboss

import android.os.Bundle
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
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.ui.ScheduleScreen
import com.gpowell.bdoboss.ui.SettingsScreen
import com.gpowell.bdoboss.ui.TimersScreen
import com.gpowell.bdoboss.ui.theme.BdoBossTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BdoBossTheme {
                var tab by remember { mutableIntStateOf(0) }
                var schedule by remember { mutableStateOf<Schedule?>(null) }
                var spawns by remember { mutableStateOf<List<Spawn>>(emptyList()) }

                LaunchedEffect(Unit) {
                    val loaded = withContext(Dispatchers.IO) {
                        ScheduleRepository(this@MainActivity).load()
                    }
                    schedule = loaded
                    spawns = SpawnCalculator.upcoming(loaded, Instant.now(), 40)
                    lifecycleScope.launch { AlarmScheduler.rearm(this@MainActivity) }
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
                            0 -> TimersScreen(spawns)
                            1 -> schedule?.let { ScheduleScreen(it) }
                            2 -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
