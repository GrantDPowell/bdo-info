package com.gpowell.bdoboss.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.QuietRule
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.notify.NotificationHelper
import com.gpowell.bdoboss.ui.theme.BdoGold
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LEAD_CHOICES = listOf(5, 10, 15, 30, 60)

@Composable
fun AlertsScreen() {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = NotificationSettings())

    fun save(transform: (NotificationSettings) -> NotificationSettings) {
        scope.launch {
            withContext(NonCancellable) {
                repo.update(transform)
                AlarmScheduler.rearm(ctx.applicationContext)
            }
        }
    }

    fun updateRule(updated: QuietRule) = save {
        it.copy(quietRules = it.quietRules.map { r -> if (r.id == updated.id) updated else r })
    }

    var hasNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotifPermission = granted }

    val alarmMgr = remember { ctx.getSystemService(AlarmManager::class.java) }
    // Re-checked on every resume so that returning from the system exact-alarm screen
    // reflects the updated grant without requiring an app restart.
    var needsExactAlarmGrant by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 31 && !alarmMgr.canScheduleExactAlarms())
    }
    LifecycleResumeEffect(Unit) {
        needsExactAlarmGrant = Build.VERSION.SDK_INT >= 31 && !alarmMgr.canScheduleExactAlarms()
        onPauseOrDispose { /* nothing to clean up */ }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        if (!hasNotifPermission) {
            item {
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Notifications are blocked", Modifier.weight(1f))
                        Button({ permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                }
            }
        }
        if (needsExactAlarmGrant) {
            item {
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Exact alarms off — reminders may be late", Modifier.weight(1f))
                        Button({
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                },
                            )
                        }) { Text("Fix") }
                    }
                }
            }
        }

        item { SectionHeader("GENERAL") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Boss notifications", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Switch(
                    checked = settings.masterEnabled,
                    onCheckedChange = { on -> save { it.copy(masterEnabled = on) } },
                )
            }
        }
        item {
            Button(
                onClick = { NotificationHelper.showBossReminder(ctx, "Kzarka", 15) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send test notification") }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("QUIET HOURS", Modifier.weight(1f))
                TextButton(onClick = {
                    save {
                        val newId = (it.quietRules.maxOfOrNull { r -> r.id } ?: 0L) + 1
                        it.copy(quietRules = it.quietRules + QuietRule(id = newId))
                    }
                }) { Text("+ Add", color = BdoGold) }
            }
        }
        if (settings.quietRules.isEmpty()) {
            item {
                Text(
                    "No quiet hours — you'll get pinged 24/7.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(settings.quietRules, key = { it.id }) { rule ->
            QuietRuleCard(
                rule = rule,
                onUpdate = ::updateRule,
                onDelete = {
                    save { it.copy(quietRules = it.quietRules.filterNot { r -> r.id == rule.id }) }
                },
            )
        }

        item { SectionHeader("BOSSES") }
        items(NotificationSettings.ALL_DEFAULT_ON.sorted()) { boss ->
            BossRow(boss, settings, ::save)
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = BdoGold,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun BossRow(
    boss: String,
    settings: NotificationSettings,
    save: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val enabled = boss in settings.enabledBosses
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BossIcon(boss, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                Text(boss, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        save {
                            it.copy(
                                enabledBosses =
                                    if (on) it.enabledBosses + boss else it.enabledBosses - boss,
                            )
                        }
                    },
                )
            }
            if (enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LEAD_CHOICES.forEach { lead ->
                        val selected = lead in settings.leadsFor(boss)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                save {
                                    val cur = it.leadsFor(boss)
                                    val next = if (selected) cur - lead else cur + lead
                                    it.copy(
                                        leadsByBoss = it.leadsByBoss +
                                            (boss to next.ifEmpty { setOf(NotificationSettings.DEFAULT_LEAD_MIN) }),
                                    )
                                }
                            },
                            label = { Text("${lead}m") },
                        )
                    }
                }
            }
        }
    }
}
