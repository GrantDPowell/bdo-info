package com.gpowell.bdoboss.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpowell.bdoboss.data.QuietRule
import com.gpowell.bdoboss.ui.theme.BdoGold
import java.time.DayOfWeek

/** SUNDAY..SATURDAY display order with single-letter chip labels. */
private val DAY_ORDER: List<Pair<String, String>> = listOf(
    DayOfWeek.SUNDAY to "S", DayOfWeek.MONDAY to "M", DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W", DayOfWeek.THURSDAY to "T", DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
).map { (d, l) -> d.name to l }

/** "h:mm AM/PM" for a minute-of-day value. */
internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    val h24 = minuteOfDay / 60
    val m = minuteOfDay % 60
    val amPm = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "%d:%02d %s".format(h12, m, amPm)
}

@Composable
fun QuietRuleCard(
    rule: QuietRule,
    onUpdate: (QuietRule) -> Unit,
    onDelete: () -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rule.label,
                    Modifier.weight(1f).clickable { showRename = true },
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { on -> onUpdate(rule.copy(enabled = on)) },
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete rule",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showStartPicker = true }) {
                    Text(formatMinuteOfDay(rule.startMin), color = BdoGold)
                }
                Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showEndPicker = true }) {
                    Text(formatMinuteOfDay(rule.endMin), color = BdoGold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_ORDER.forEach { (dayName, letter) ->
                    DayToggle(
                        letter = letter,
                        selected = dayName in rule.days,
                        onToggle = {
                            val next =
                                if (dayName in rule.days) rule.days - dayName
                                else rule.days + dayName
                            onUpdate(rule.copy(days = next))
                        },
                    )
                }
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinuteOfDay = rule.startMin,
            onConfirm = { v ->
                onUpdate(rule.copy(startMin = v))
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinuteOfDay = rule.endMin,
            onConfirm = { v ->
                onUpdate(rule.copy(endMin = v))
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
    if (showRename) {
        RenameDialog(
            current = rule.label,
            onConfirm = { name ->
                onUpdate(rule.copy(label = name))
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, onToggle: () -> Unit) {
    val bg = if (selected) BdoGold else androidx.compose.ui.graphics.Color.Transparent
    val fg =
        if (selected) androidx.compose.ui.graphics.Color.Black
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(bg, CircleShape)
            .border(
                width = 1.dp,
                color = if (selected) BdoGold else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename rule") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Label") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim().ifEmpty { current }) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
