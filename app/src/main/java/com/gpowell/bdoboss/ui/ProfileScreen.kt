package com.gpowell.bdoboss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gpowell.bdoboss.data.SettingsRepository

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val apiKey by repo.apiKeyFlow.collectAsState(initial = "")

    if (apiKey.isBlank()) {
        LockedFeature(
            title = "Adventurer Profile",
            blurb = "Look up any Family or character: gear, lifeskills, and guild — powered by BDO Alerts.",
            bullets = listOf("Gearscore & class breakdown", "Lifeskill levels", "Guild & node-war history"),
            onOpenSettings = onOpenSettings,
        )
    } else {
        KeyedPlaceholder("✓ Key saved — Profile arrives in the next build")
    }
}
