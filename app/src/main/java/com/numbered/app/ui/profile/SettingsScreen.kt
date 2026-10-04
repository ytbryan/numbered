package com.numbered.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.components.ExplanationHelp
import com.numbered.app.security.AppLockSetting
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.locale
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.toLocalDate
import com.numbered.app.appContainer
import com.numbered.app.domain.MAX_PRIORITIES_PER_WEEK
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var birthDateVisible by remember { mutableStateOf(false) }
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val themes = LocalContext.current.appContainer.themes
    val selectedTheme by themes.selection.collectAsStateWithLifecycle()
    val titleStyles = LocalContext.current.appContainer.titleStyles
    val selectedTitleStyles by titleStyles.styles.collectAsStateWithLifecycle()
    val weekProgress = LocalContext.current.appContainer.weekProgress
    val selectedWeekProgress by weekProgress.style.collectAsStateWithLifecycle()
    val ageDisplay = LocalContext.current.appContainer.ageDisplay
    val decimalAgeEnabled by ageDisplay.decimalEnabled.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val data = containerViewModel { DataViewModel(it, resolver) }
    val lastExport by data.lastExport.collectAsStateWithLifecycle()
    val recoveryDate by data.recoveryDate.collectAsStateWithLifecycle()
    NoticeEffect(data.notices)
    val autoBackup = containerViewModel { AutoBackupViewModel(it) }
    NoticeEffect(autoBackup.notices)
    val import = rememberImport(data)
    ExportDialog(data)
    ImportDialog(data, replacing = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    ExplanationHelp(
                        title = stringResource(R.string.settings_help_title),
                        body = stringResource(R.string.gentle_details) + "\n\n" + stringResource(R.string.week_starts_help),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = profile ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BirthDateField(
                current.birthDate,
                today,
                visible = birthDateVisible,
                onToggleVisibility = { birthDateVisible = !birthDateVisible },
                onChange = viewModel::setBirthDate,
            )
            HorizonField(current.horizonYears, current.birthDate, today, viewModel::setHorizon)
            GentleField(current.gentle, viewModel::setGentle)
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = decimalAgeEnabled,
                    role = Role.Switch,
                    onValueChange = ageDisplay::setDecimalEnabled,
                ).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.decimal_age), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.decimal_age_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = decimalAgeEnabled, onCheckedChange = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.priorities_per_week), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.priorities_per_week_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val decreaseLabel = stringResource(R.string.decrease_priorities)
                    val increaseLabel = stringResource(R.string.increase_priorities)
                    TextButton(
                        onClick = { viewModel.setPrioritiesPerWeek(current.prioritiesPerWeek - 1) },
                        enabled = current.prioritiesPerWeek > 1,
                        modifier = Modifier.semantics { contentDescription = decreaseLabel },
                    ) { Text(stringResource(R.string.action_decrease)) }
                    Text(
                        current.prioritiesPerWeek.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    TextButton(
                        onClick = { viewModel.setPrioritiesPerWeek(current.prioritiesPerWeek + 1) },
                        enabled = current.prioritiesPerWeek < MAX_PRIORITIES_PER_WEEK,
                        modifier = Modifier.semantics { contentDescription = increaseLabel },
                    ) { Text(stringResource(R.string.action_increase)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = current.otherThingsDoneEnabled,
                    role = Role.Switch,
                    onValueChange = viewModel::setOtherThingsDoneEnabled,
                ).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.other_things_done), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.other_things_done_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = current.otherThingsDoneEnabled, onCheckedChange = null)
            }
            ThemePicker(selectedTheme, themes::select)
            TitleStylePicker(selectedTitleStyles, titleStyles::select)
            WeekProgressPicker(selectedWeekProgress, weekProgress::select)
            AppLockSetting()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RemindersSection(
                settings = reminders,
                firstDay = current.firstDayOfWeek,
                canNotify = viewModel::canNotify,
                onToggle = viewModel::setReminder,
                onTime = viewModel::setReminderTime,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.week_starts_on, current.firstDayOfWeek.getDisplayName(TextStyle.FULL, locale())),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.week_starts_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.data_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DataAction(Icons.Outlined.SaveAlt, stringResource(R.string.export_title), stringResource(R.string.export_body), data::startExport)
                Text(
                    lastExport?.let { stringResource(R.string.last_export_date, shortDate(it.toLocalDate(), today)) }
                        ?: stringResource(R.string.last_export_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AutoBackupSetting(autoBackup)
                DataAction(Icons.Outlined.Restore, stringResource(R.string.import_title), stringResource(R.string.import_body), import)
                recoveryDate?.let { savedAt ->
                    DataAction(
                        Icons.Outlined.Restore,
                        stringResource(R.string.recovery_title),
                        stringResource(R.string.recovery_body, shortDate(savedAt.toLocalDate(), today)),
                        data::restoreRecovery,
                    )
                }
            }
            ExplanationHelp(
                title = stringResource(R.string.about_numbered),
                body = stringResource(R.string.name_origin),
                buttonLabel = stringResource(R.string.about_numbered),
            )
        }
    }
}
