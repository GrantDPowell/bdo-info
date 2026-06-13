package com.gpowell.bdoboss.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gpowell.bdoboss.data.LiveSpawnCache
import com.gpowell.bdoboss.data.NotificationSettings
import com.gpowell.bdoboss.data.QuietRule
import com.gpowell.bdoboss.data.SettingsRepository
import com.gpowell.bdoboss.notify.AlarmScheduler
import com.gpowell.bdoboss.notify.NotificationHelper
import com.gpowell.bdoboss.ui.theme.BdoCard
import com.gpowell.bdoboss.ui.theme.BdoChip
import com.gpowell.bdoboss.ui.theme.BdoColors
import com.gpowell.bdoboss.ui.theme.Diamond
import com.gpowell.bdoboss.ui.theme.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LEAD_CHOICES = listOf(5, 10, 15, 30, 60)

@Composable
private fun goldSwitch() = SwitchDefaults.colors(
    checkedThumbColor = BdoColors.onGold,
    checkedTrackColor = BdoColors.gold,
    checkedBorderColor = BdoColors.gold,
    uncheckedThumbColor = BdoColors.onMuted,
    uncheckedTrackColor = BdoColors.surfaceHi,
    uncheckedBorderColor = BdoColors.line,
)

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

    // Field bosses (e.g. Black Shadow) seen in the live feed but not in the fixed roster.
    val fieldBosses by produceState(initialValue = emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            LiveSpawnCache.load(ctx).flatMap { it.bosses }.distinct()
                .filterNot { it in NotificationSettings.ALL_DEFAULT_ON }
                .sorted()
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
    var needsExactAlarmGrant by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 31 && !alarmMgr.canScheduleExactAlarms())
    }
    LifecycleResumeEffect(Unit) {
        needsExactAlarmGrant = Build.VERSION.SDK_INT >= 31 && !alarmMgr.canScheduleExactAlarms()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
    ) {
        if (!hasNotifPermission) {
            item { WarnCard("Notifications are blocked", "Allow", danger = true) { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
        }
        if (needsExactAlarmGrant) {
            item {
                WarnCard("Exact alarms off — reminders may be late", "Fix", danger = true) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:${ctx.packageName}")
                        },
                    )
                }
            }
        }

        item { SectionLabel("General") }
        item {
            BdoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Boss notifications", fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                        Text(
                            "Exact alarms fire even offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BdoColors.onFaint,
                        )
                    }
                    Switch(
                        checked = settings.masterEnabled,
                        onCheckedChange = { on -> save { it.copy(masterEnabled = on) } },
                        colors = goldSwitch(),
                    )
                }
            }
        }
        item {
            TextButton(
                onClick = { NotificationHelper.showBossReminder(ctx, "Kzarka", 15) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send test notification", color = BdoColors.goldHi) }
        }

        item {
            SectionLabel(
                "Quiet hours",
                trailing = {
                    TextButton(onClick = {
                        save {
                            val newId = (it.quietRules.maxOfOrNull { r -> r.id } ?: 0L) + 1
                            it.copy(quietRules = it.quietRules + QuietRule(id = newId))
                        }
                    }) { Text("+ Add", color = BdoColors.goldHi) }
                },
            )
        }
        if (settings.quietRules.isEmpty()) {
            item {
                Text(
                    "No quiet hours — you'll get pinged 24/7.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BdoColors.onFaint,
                )
            }
        }
        items(settings.quietRules, key = { it.id }) { rule ->
            QuietRuleCard(
                rule = rule,
                onUpdate = ::updateRule,
                onDelete = { save { it.copy(quietRules = it.quietRules.filterNot { r -> r.id == rule.id }) } },
            )
        }

        item { SectionLabel("World bosses") }
        items(NotificationSettings.ALL_DEFAULT_ON.sorted()) { boss ->
            BossAlertRow(boss, settings, ::save)
        }

        if (fieldBosses.isNotEmpty()) {
            item {
                SectionLabel(
                    "Field bosses",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Diamond(size = 5.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("live", style = MaterialTheme.typography.labelSmall, color = BdoColors.onFaint)
                        }
                    },
                )
            }
            items(fieldBosses) { boss -> BossAlertRow(boss, settings, ::save) }
        }
    }
}

@Composable
private fun WarnCard(text: String, action: String, danger: Boolean, onClick: () -> Unit) {
    val accent = if (danger) BdoColors.down else BdoColors.gold
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BdoColors.surface1)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = BdoColors.onBg)
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = BdoColors.onGold),
            ) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun BossAlertRow(
    boss: String,
    settings: NotificationSettings,
    save: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val enabled = boss in settings.enabledBosses
    BdoCard(Modifier.fillMaxWidth(), glow = enabled, contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BossTile(boss, size = 40.dp, glow = enabled)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(boss, fontWeight = FontWeight.SemiBold, color = BdoColors.onBg)
                Text(
                    if (enabled) "Alert ${settings.leadsFor(boss).sorted().joinToString("/") { "${it}m" }} before" else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) BdoColors.goldHi else BdoColors.onFaint,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    save {
                        it.copy(enabledBosses = if (on) it.enabledBosses + boss else it.enabledBosses - boss)
                    }
                },
                colors = goldSwitch(),
            )
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LEAD_CHOICES.forEach { lead ->
                    val selected = lead in settings.leadsFor(boss)
                    BdoChip(
                        text = "${lead}m",
                        active = selected,
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
                    )
                }
            }
        }
    }
}
