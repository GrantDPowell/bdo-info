package com.gpowell.bdoboss.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.BuildConfig
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.launch

/**
 * App settings overlay (opened from the Hub): API key, animated effects, theme palette,
 * and About. Credits + data-source status live in [CreditsScreen].
 *
 * The BDO Alerts API key is stored in DataStore only — never logged, and the saved value
 * is never echoed back into the field by default.
 */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val savedKey by repo.apiKeyFlow.collectAsState(initial = "")
    val effectsEnabled by repo.effectsEnabledFlow.collectAsState(initial = true)
    val eclipse by repo.eclipseThemeFlow.collectAsState(initial = false)

    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    OverlayScaffold(title = "Settings", onBack = onBack) {
        SettingsSectionHeader("BDO ALERTS API KEY")
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            visualTransformation =
                if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key",
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BdoGold,
                focusedLabelColor = BdoGold,
                cursorColor = BdoGold,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    val trimmed = keyInput.trim()
                    scope.launch { repo.setApiKey(trimmed) }
                    keyInput = ""
                    keyVisible = false
                },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            if (savedKey.isNotBlank()) {
                TextButton(onClick = { showClearConfirm = true }) { Text("Clear key") }
            }
        }
        Text(
            if (savedKey.isBlank()) "No key — Events & Profile are locked" else "Key saved ✓",
            style = MaterialTheme.typography.bodySmall,
            color = if (savedKey.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else BdoGold,
        )
        Text(
            "Used for the Events and Profile tabs. Apply at bdoalerts.net (free, ~24–48h) — " +
                "the key is stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSectionHeader("APPEARANCE")
        ToggleCard(
            title = "Eclipse theme",
            subtitle = "Switch from warm gold (Vitrine) to a cool obsidian-blue palette with a cyan glow.",
            checked = eclipse,
            onChange = { scope.launch { repo.setEclipseTheme(it) } },
        )
        ToggleCard(
            title = "Living effects",
            subtitle = "Gold dust, glow, shimmer & spark bursts. Turn off for a calm, battery-light UI.",
            checked = effectsEnabled,
            onChange = { scope.launch { repo.setEffectsEnabled(it) } },
        )

        SettingsSectionHeader("ABOUT")
        Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Version", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "BDO Info is an unofficial fan-made companion for Black Desert Online (NA). " +
                        "Not affiliated with or endorsed by Pearl Abyss.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinkRow("Privacy Policy", "https://grantdpowell.github.io/bdo-info/privacy.html")
                LinkRow("GitHub", "https://github.com/GrantDPowell/bdo-info")
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear API key?") },
            text = { Text("Events and Profile will lock until a new key is saved.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.setApiKey("") }
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BdoColors.onGold,
                    checkedTrackColor = BdoColors.gold,
                ),
            )
        }
    }
}

@Composable
internal fun LinkRow(label: String, url: String) {
    val ctx = LocalContext.current
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .padding(vertical = 4.dp),
        color = BdoGold,
        fontWeight = FontWeight.SemiBold,
    )
}
