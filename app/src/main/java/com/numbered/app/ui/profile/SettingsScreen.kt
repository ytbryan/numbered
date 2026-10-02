package com.numbered.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.locale
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { SettingsViewModel(it) }
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val data = containerViewModel { DataViewModel(it, resolver) }
    NoticeEffect(data.notices)
    val import = rememberImport(data)
    ExportDialog(data)
    ImportDialog(data, replacing = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
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
            BirthDateField(current.birthDate, today, viewModel::setBirthDate)
            HorizonField(current.horizonYears, current.birthDate, today, viewModel::setHorizon)
            GentleField(current.gentle, viewModel::setGentle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.week_starts_on, current.firstDayOfWeek.getDisplayName(TextStyle.FULL, locale())),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.week_starts_help),
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
                DataAction(Icons.Outlined.Restore, stringResource(R.string.import_title), stringResource(R.string.import_body), import)
            }
            Text(
                stringResource(R.string.name_origin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
