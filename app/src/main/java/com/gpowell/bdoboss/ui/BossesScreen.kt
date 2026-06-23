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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.domain.Spawn
import com.gpowell.bdoboss.ui.theme.BdoSubTabs

private val SUB_TABS = listOf("Dial", "Timers", "Agenda", "World", "Alerts")

/**
 * "Bosses" top-level tab: Dial / Timers / Agenda / Alerts behind a secondary tab row.
 * All spawn data is live (from [spawns]); there is no static weekly schedule.
 */
@Composable
fun BossesScreen(
    spawns: List<Spawn>,
    onSpawnClick: (Spawn) -> Unit,
    onOpenSettings: () -> Unit = {},
    headerContent: (@Composable () -> Unit)? = null,
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BdoSubTabs(
            tabs = SUB_TABS,
            selected = subTab,
            onSelect = { subTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
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
                    0 -> SpawnDial(
                        spawns = spawns,
                        onSpawnClick = onSpawnClick,
                        headerContent = headerContent,
                    )
                    1 -> TimersScreen(
                        spawns,
                        onSpawnClick = onSpawnClick,
                        headerContent = headerContent,
                    )
                    2 -> ScheduleScreen(spawns, onSpawnClick = onSpawnClick)
                    3 -> WorldScreen(onOpenSettings = onOpenSettings)
                    4 -> AlertsScreen()
                }
            }
        }
    }
}
