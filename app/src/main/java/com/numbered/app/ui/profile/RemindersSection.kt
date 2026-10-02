package com.numbered.app.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.numbered.app.R
import com.numbered.app.reminders.Reminder
import com.numbered.app.reminders.ReminderSettings
import com.numbered.app.reminders.dayOf
import com.numbered.app.ui.locale
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** The weekly reminders, with the notification permission asked for only when one is turned on. */
@Composable
fun RemindersSection(
    settings: ReminderSettings,
    firstDay: DayOfWeek,
    canNotify: () -> Boolean,
    onToggle: (Reminder, Boolean) -> Unit,
    onTime: (Reminder, LocalTime) -> Unit,
) {
    val context = LocalContext.current
    var allowed by rememberSaveable { mutableStateOf(canNotify()) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    // People can change notifications in system settings and come back.
    LifecycleResumeEffect(Unit) {
        allowed = canNotify()
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = canNotify()
        val reminder = asking?.let(Reminder::valueOf)
        asking = null
        if (granted) reminder?.let { onToggle(it, true) } else refused = true
    }
    fun turnOn(reminder: Reminder) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> {
                asking = reminder.name
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Turned on anyway, so it works as soon as notifications are allowed again.
            else -> {
                onToggle(reminder, true)
                allowed = canNotify()
                refused = !allowed
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.reminders_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Reminder.entries.forEach { reminder ->
            ReminderRow(
                title = stringResource(reminder.title),
                day = reminder.dayOf(firstDay),
                time = settings.timeOf(reminder),
                on = settings.isOn(reminder),
                onToggle = { on -> if (on) turnOn(reminder) else onToggle(reminder, false) },
                onPickTime = { picking = reminder.name },
            )
        }
        if (!allowed && (refused || settings.planOn || settings.closeOn)) {
            Column {
                Text(
                    stringResource(R.string.reminders_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ) { Text(stringResource(R.string.action_open_settings)) }
            }
        }
    }

    picking?.let(Reminder::valueOf)?.let { reminder ->
        ReminderTimeDialog(
            initial = settings.timeOf(reminder),
            onPicked = { onTime(reminder, it) },
            onDismiss = { picking = null },
        )
    }
}

private val Reminder.title: Int
    get() = when (this) {
        Reminder.Plan -> R.string.reminder_plan_title
        Reminder.Close -> R.string.close_prompt_title
    }

@Composable
private fun ReminderRow(
    title: String,
    day: DayOfWeek,
    time: LocalTime,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    val context = LocalContext.current
    val locale = locale()
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val formatted = time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    val change = stringResource(R.string.a11y_change_time, title)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            // No side padding, so the time lines up under its title.
            TextButton(
                onClick = onPickTime,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.semantics { contentDescription = change },
            ) {
                Text(stringResource(R.string.reminder_when, day.getDisplayName(TextStyle.FULL, locale), formatted))
            }
        }
        Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = title })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onPicked: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    val colors = MaterialTheme.colorScheme
    // Built from a basic dialog so its surface matches every other dialog in the app.
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    stringResource(R.string.reminder_time_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                TimePicker(
                    state,
                    // Tertiary marks planned weeks elsewhere, so the period selector stays in the primary family.
                    colors = TimePickerDefaults.colors(
                        containerColor = colors.surfaceContainerHigh,
                        clockDialColor = colors.surfaceContainerHighest,
                        periodSelectorSelectedContainerColor = colors.primaryContainer,
                        periodSelectorSelectedContentColor = colors.onPrimaryContainer,
                        periodSelectorUnselectedContentColor = colors.onSurfaceVariant,
                        periodSelectorBorderColor = colors.outline,
                        timeSelectorUnselectedContainerColor = colors.surfaceContainerHighest,
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = {
                            onPicked(LocalTime.of(state.hour, state.minute))
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}
