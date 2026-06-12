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
            title = "Profile needs a BDO Alerts API key",
            onOpenSettings = onOpenSettings,
        )
    } else {
        KeyedPlaceholder("✓ Key saved — Profile arrives in the next build")
    }
}
