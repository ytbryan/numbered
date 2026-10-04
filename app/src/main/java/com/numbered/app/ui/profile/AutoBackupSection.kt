package com.numbered.app.ui.profile

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.backup.AutoBackupProblem
import com.numbered.app.backup.AutoBackupResult
import com.numbered.app.backup.AutoBackupStatus
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AutoBackupDialog { Setup, Manage }

/** Turns automatic copies on and off, and reports how the last one went. */
class AutoBackupViewModel(private val container: AppContainer) : NoticeViewModel() {
    private val backup = container.autoBackup
    private val dialog = MutableStateFlow<AutoBackupDialog?>(null)

    /**
     * The passphrase chosen for setup, held only in memory while the folder picker is open, so
     * setup stops rather than saving unprotected copies if the app is recreated meanwhile.
     */
    private var planned: Planned? = null

    private class Planned(val passphrase: String?)

    val status: StateFlow<AutoBackupStatus> = backup.status
    val running: StateFlow<Boolean> = backup.running
    val today: StateFlow<LocalDate> = container.today.value
    val openDialog: StateFlow<AutoBackupDialog?> = dialog.asStateFlow()

    fun open() {
        dialog.value = if (status.value.enabled && status.value.problem == null) AutoBackupDialog.Manage else AutoBackupDialog.Setup
    }

    fun changeFolder() {
        dialog.value = AutoBackupDialog.Setup
    }

    fun dismiss() {
        dialog.value = null
    }

    /** Remembers the protection chosen, just before the folder picker opens. */
    fun choosePassphrase(passphrase: String) {
        planned = Planned(passphrase.takeIf { it.isNotEmpty() })
        dialog.value = null
    }

    fun cancelFolder() {
        planned = null
    }

    fun noFolderPicker() {
        planned = null
        notify(Notice(R.string.notice_no_file_picker))
    }

    // Saving runs in the app's scope, so leaving Settings never cuts a copy short.
    fun folderChosen(tree: Uri) {
        val plan = planned ?: return notify(Notice(R.string.notice_export_interrupted))
        planned = null
        container.scope.launch { report(backup.enable(tree, plan.passphrase), setup = true) }
    }

    fun backUpNow() {
        dialog.value = null
        container.scope.launch { report(backup.run(), setup = false) }
    }

    fun turnOff() {
        dialog.value = null
        container.scope.launch {
            backup.disable()
            notify(Notice(R.string.notice_auto_backup_off))
        }
    }

    private fun report(result: AutoBackupResult, setup: Boolean) {
        val folder = status.value.folderName
        when (result) {
            AutoBackupResult.Saved -> notify(Notice(R.string.notice_auto_backup_saved, listOf(folder)))
            AutoBackupResult.Unchanged -> notify(Notice(R.string.notice_auto_backup_unchanged))
            AutoBackupResult.FolderUnavailable -> notify(
                if (setup) Notice(R.string.notice_auto_backup_folder_refused)
                else Notice(R.string.notice_auto_backup_folder_failed, listOf(folder)),
            )
            AutoBackupResult.PassphraseUnavailable -> notify(
                Notice(if (setup) R.string.notice_auto_backup_passphrase_failed else R.string.notice_auto_backup_passphrase_lost),
            )
            AutoBackupResult.Off, AutoBackupResult.NotSetUp -> Unit
        }
    }
}

/** The Settings row for automatic copies, with its setup and manage dialogs. */
@Composable
fun AutoBackupSetting(viewModel: AutoBackupViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val dialog by viewModel.openDialog.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.folderChosen(tree) else viewModel.cancelFolder()
    }
    DataAction(
        Icons.Outlined.Backup,
        stringResource(R.string.auto_backup_title),
        if (running) stringResource(R.string.backing_up) else statusText(status, viewModel),
        viewModel::open,
        problem = status.problem != null && !running,
    )
    when (dialog) {
        AutoBackupDialog.Setup -> SetupDialog(viewModel) { passphrase ->
            viewModel.choosePassphrase(passphrase)
            try {
                launcher.launch(null)
            } catch (_: ActivityNotFoundException) {
                viewModel.noFolderPicker()
            }
        }
        AutoBackupDialog.Manage -> ManageDialog(status, running, viewModel)
        null -> Unit
    }
}

@Composable
private fun statusText(status: AutoBackupStatus, viewModel: AutoBackupViewModel): String {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val folder = status.folderName
    val saved = status.lastSavedAt
    return when {
        !status.enabled -> stringResource(R.string.auto_backup_off)
        status.problem == AutoBackupProblem.FolderUnavailable -> stringResource(R.string.auto_backup_folder_problem, folder)
        status.problem == AutoBackupProblem.PassphraseUnavailable -> stringResource(R.string.auto_backup_passphrase_problem)
        saved == null -> stringResource(R.string.auto_backup_off)
        else -> stringResource(
            if (status.protected) R.string.auto_backup_on_protected else R.string.auto_backup_on,
            folder,
            shortDate(saved.toLocalDate(), today),
        )
    }
}

/** Explains automatic copies and offers a passphrase, then hands over to the folder picker. */
@Composable
private fun SetupDialog(viewModel: AutoBackupViewModel, onChooseFolder: (String) -> Unit) {
    // Plain remember, not saveable: a passphrase never goes into saved instance state.
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = repeat.isNotEmpty() && repeat != passphrase
    val ready = passphrase.isEmpty() || (!tooShort && repeat == passphrase)
    val choose = { if (ready) onChooseFolder(passphrase) }
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(stringResource(R.string.auto_backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.auto_backup_setup_body))
                Text(
                    stringResource(R.string.auto_backup_retention),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.passphrase_optional),
                    error = if (tooShort) stringResource(R.string.passphrase_too_short) else null,
                    imeAction = if (passphrase.isEmpty()) ImeAction.Done else ImeAction.Next,
                    onDone = choose,
                )
                if (passphrase.isNotEmpty()) {
                    PassphraseField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = stringResource(R.string.passphrase_repeat),
                        error = if (mismatch) stringResource(R.string.passphrase_mismatch) else null,
                        imeAction = ImeAction.Done,
                        onDone = choose,
                    )
                    Text(
                        stringResource(R.string.auto_backup_passphrase_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = choose, enabled = ready) { Text(stringResource(R.string.action_choose_folder)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ManageDialog(status: AutoBackupStatus, running: Boolean, viewModel: AutoBackupViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        title = { Text(stringResource(R.string.auto_backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(statusText(status, viewModel))
                Text(
                    stringResource(R.string.auto_backup_restore_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    TextButton(onClick = viewModel::backUpNow, enabled = !running) {
                        Text(stringResource(R.string.action_back_up_now))
                    }
                    TextButton(onClick = viewModel::changeFolder, enabled = !running) {
                        Text(stringResource(R.string.action_change_folder))
                    }
                    TextButton(onClick = viewModel::turnOff, enabled = !running) {
                        Text(stringResource(R.string.action_turn_off))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
