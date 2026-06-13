package com.gpowell.bdoboss.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpowell.bdoboss.BuildConfig
import com.gpowell.bdoboss.data.DataSource
import com.gpowell.bdoboss.data.DataSources
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.data.SourceStatus
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.launch

/**
 * Full-screen app settings, opened from the top bar gear. Rendered as an overlay
 * above the whole Scaffold; back arrow and system back both close it.
 *
 * The BDO Alerts API key is stored in DataStore only — never logged, and the saved
 * value is never echoed back into the text field by default.
 */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val savedKey by repo.apiKeyFlow.collectAsState(initial = "")

    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Live up/down status for each data source, checked concurrently on open.
    val sourceStatus = remember { mutableStateMapOf<String, SourceStatus>() }
    LaunchedEffect(Unit) {
        DataSources.all.forEach { src ->
            sourceStatus[src.name] = SourceStatus.CHECKING
            launch { sourceStatus[src.name] = DataSources.status(src) }
        }
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            0f to Color(0xFF0B0A08),
            0.5f to Color(0xFF14110C),
            1f to Color(0xFF0B0A08),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            // Swallow touches so the screens underneath the overlay stay inert.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = BdoGold,
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text("Clear key")
                    }
                }
            }
            Text(
                if (savedKey.isBlank()) "No key — Events & Profile are locked" else "Key saved ✓",
                style = MaterialTheme.typography.bodySmall,
                color = if (savedKey.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    BdoGold
                },
            )
            Text(
                "Used for the Events and Profile tabs. Apply at bdoalerts.net — " +
                    "the key is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    LinkRow("Privacy Policy", "https://grantdpowell.github.io/bdo-info/privacy.html")
                    LinkRow("GitHub", "https://github.com/GrantDPowell/bdo-info")
                }
            }

            SettingsSectionHeader("DATA SOURCES")
            Text(
                "The APIs and data BDO Info is built on, with live status. Tap any " +
                    "source to visit it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DataSources.all.forEach { src ->
                DataSourceCard(
                    source = src,
                    status = sourceStatus[src.name] ?: SourceStatus.CHECKING,
                )
            }

            Spacer(Modifier.width(0.dp)) // bottom breathing room via spacedBy
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
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelSmall,
        color = BdoGold,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun LinkRow(label: String, url: String) {
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

@Composable
private fun DataSourceCard(source: DataSource, status: SourceStatus) {
    val ctx = LocalContext.current
    Card(
        onClick = {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.siteUrl)))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                StatusPill(status)
            }
            Text(
                source.role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                source.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(status: SourceStatus) {
    val checkingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val (label, color) = when (status) {
        SourceStatus.UP -> "Online" to Color(0xFF6FBF5A)
        SourceStatus.DOWN -> "Offline" to Color(0xFFE0593F)
        SourceStatus.BUNDLED -> "Bundled" to BdoGold
        SourceStatus.CHECKING -> "Checking…" to checkingColor
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}
