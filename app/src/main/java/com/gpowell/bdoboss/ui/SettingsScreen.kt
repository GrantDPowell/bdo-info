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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.notify.NotificationHelper
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LEAD_CHOICES = listOf(5, 10, 15, 30, 60)

@Composable
fun SettingsScreen() {
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiet hours", Modifier.weight(1f))
                    Switch(
                        checked = settings.quietEnabled,
                        onCheckedChange = { on -> save { it.copy(quietEnabled = on) } },
                    )
                }
                if (settings.quietEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MinuteField("From", settings.quietStartMin) { v ->
                            save { it.copy(quietStartMin = v) }
                        }
                        MinuteField("To", settings.quietEndMin) { v ->
                            save { it.copy(quietEndMin = v) }
                        }
                    }
                }
            }
        }
        items(NotificationSettings.ALL_DEFAULT_ON.sorted()) { boss ->
            BossRow(boss, settings, ::save)
        }
        item {
            Button(
                onClick = { NotificationHelper.showBossReminder(ctx, "Kzarka", 15) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send test notification") }
        }
    }
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

@Composable
private fun MinuteField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var text by remember(minuteOfDay) {
        mutableStateOf("%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            Regex("^(\\d{1,2}):(\\d{2})$").find(v)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toInt()
                if (h in 0..23 && min in 0..59) onChange(h * 60 + min)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(110.dp),
    )
}
