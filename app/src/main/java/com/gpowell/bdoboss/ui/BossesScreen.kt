package com.gpowell.bdoboss.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gpowell.bdoboss.data.Schedule
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoGold

private val SUB_TABS = listOf("Timers", "Schedule", "Alerts")

/**
 * "Bosses" top-level tab: hosts the three v1 screens (Timers / Schedule / Alerts)
 * behind a secondary tab row. The child screens are unchanged.
 */
@Composable
fun BossesScreen(
    spawns: List<Spawn>,
    schedule: Schedule?,
    onSpawnClick: (Spawn) -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = Color.Transparent,
            contentColor = BdoGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = BdoGold,
                )
            },
        ) {
            SUB_TABS.forEachIndexed { i, title ->
                Tab(
                    selected = subTab == i,
                    onClick = { subTab = i },
                    text = { Text(title) },
                    selectedContentColor = BdoGold,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedContent(
            targetState = subTab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 8 * dir } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally { -it / 8 * dir } + fadeOut(tween(160)))
            },
            label = "bossesSubTab",
        ) { t ->
            Box(Modifier.fillMaxSize()) {
                when (t) {
                    0 -> TimersScreen(
                        spawns,
                        onSpawnClick = onSpawnClick,
                        headerContent = headerContent,
                    )
                    1 -> schedule?.let { ScheduleScreen(it, onSpawnClick = onSpawnClick) }
                    2 -> AlertsScreen()
                }
            }
        }
    }
}
