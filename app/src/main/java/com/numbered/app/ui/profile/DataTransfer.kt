package com.numbered.app.ui.profile

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.data.BackupFormat
import com.numbered.app.data.BackupRead
import com.numbered.app.data.Snapshot
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.toLocalDate
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Some file providers label JSON as generic data or text, so the picker offers those too. */
private val IMPORT_TYPES = arrayOf("application/json", "application/octet-stream", "text/*")
private const val EXPORT_TYPE = "application/json"

/** Where an import stands once a file has been read. */
sealed interface ImportStep {
    /** The file is protected and needs its passphrase. */
    data class Locked(val file: BackupRead.Locked, val wrongPassphrase: Boolean = false, val unlocking: Boolean = false) : ImportStep

    /** The file is read and checked, waiting for the person to confirm. */
    data class Confirm(val snapshot: Snapshot) : ImportStep
}

/** Saves everything to a file the person keeps, and reads such a file back in. */
class DataViewModel(private val container: AppContainer, private val resolver: ContentResolver) : NoticeViewModel() {
    private val repository = container.repository
    private val choosing = MutableStateFlow(false)
    private val step = MutableStateFlow<ImportStep?>(null)
    private val importing = MutableStateFlow(false)

    /**
     * The passphrase chosen for the export in progress, held only in memory while the save dialog
     * is open. If the app is recreated meanwhile, the export stops rather than writing an
     * unprotected file the person asked to protect.
     */
    private var plannedExport: ExportPlan? = null

    private class ExportPlan(val passphrase: String?)

    /** Whether the export dialog, which offers a passphrase, is open. */
    val choosingExport: StateFlow<Boolean> = choosing.asStateFlow()

    val importStep: StateFlow<ImportStep?> = step.asStateFlow()
    val importBusy: StateFlow<Boolean> = importing.asStateFlow()
    val lastExport = container.backupSafety.lastExport
    val recoveryDate = container.backupSafety.recoveryDate

    val today: StateFlow<LocalDate> = container.today.value

    fun suggestedFileName(): String = "numbered-${today.value}.json"

    fun startExport() {
        choosing.value = true
    }

    fun dismissExport() {
        choosing.value = false
    }

    /** Remembers the protection chosen, just before the save dialog opens. */
    fun chooseExport(passphrase: String?) {
        plannedExport = ExportPlan(passphrase?.takeIf { it.isNotEmpty() })
        choosing.value = false
    }

    fun cancelExport() {
        plannedExport = null
    }

    fun export(uri: Uri) = launchWrite {
        val plan = plannedExport
        plannedExport = null
        if (plan == null) {
            discard(uri)
            return@launchWrite notify(Notice(R.string.notice_export_interrupted))
        }
        val snapshot = repository.snapshot() ?: return@launchWrite
        val saved = try {
            val text = withContext(Dispatchers.Default) { BackupFormat.encode(snapshot, plan.passphrase) }
            withContext(Dispatchers.IO) {
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
                stream.use { it.write(text.toByteArray()) }
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        if (saved) container.backupSafety.recordExport(container.clock.millis())
        notify(
            Notice(
                when {
                    !saved -> R.string.notice_export_failed
                    plan.passphrase != null -> R.string.notice_exported_protected
                    else -> R.string.notice_exported
                },
            ),
        )
    }

    fun read(uri: Uri) = launchWrite {
        val text = try {
            withContext(Dispatchers.IO) {
                val stream = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
                stream.use(::readLimited)
            }
        } catch (_: IOException) {
            return@launchWrite notify(Notice(R.string.notice_import_unreadable))
        } catch (_: SecurityException) {
            return@launchWrite notify(Notice(R.string.notice_import_unreadable))
        }
        show(text?.let { withContext(Dispatchers.Default) { BackupFormat.decode(it) } } ?: BackupRead.NotABackup)
    }

    fun unlock(passphrase: String) = launchWrite {
        val locked = step.value as? ImportStep.Locked ?: return@launchWrite
        if (locked.unlocking) return@launchWrite
        step.value = locked.copy(wrongPassphrase = false, unlocking = true)
        val result = withContext(Dispatchers.Default) { BackupFormat.unlock(locked.file, passphrase) }
        // The person may have cancelled while the passphrase was being checked.
        if ((step.value as? ImportStep.Locked)?.file !== locked.file) return@launchWrite
        if (result == BackupRead.WrongPassphrase) {
            step.value = locked.copy(wrongPassphrase = true, unlocking = false)
        } else {
            step.value = null
            show(result)
        }
    }

    private fun show(result: BackupRead) {
        when (result) {
            is BackupRead.Ok -> step.value = ImportStep.Confirm(result.snapshot)
            is BackupRead.Locked -> step.value = ImportStep.Locked(result)
            BackupRead.NotABackup -> notify(Notice(R.string.notice_import_not_backup))
            BackupRead.TooNew -> notify(Notice(R.string.notice_import_too_new))
            BackupRead.Damaged -> notify(Notice(R.string.notice_import_damaged))
            BackupRead.WrongPassphrase -> notify(Notice(R.string.notice_import_damaged))
        }
    }

    fun confirmImport() = launchWrite {
        if (importing.value) return@launchWrite
        val snapshot = (step.value as? ImportStep.Confirm)?.snapshot ?: return@launchWrite
        importing.value = true
        try {
            // Hold the database transaction while saving its current contents so widget writes
            // cannot slip between the recovery copy and the replacement.
            container.database.withTransaction {
                repository.snapshot()?.let { container.backupSafety.saveRecovery(it) }
                repository.replaceAll(snapshot)
            }
            container.drafts.clearAll()
            step.value = null
            notify(Notice(R.string.notice_imported))
        } catch (_: IOException) {
            notify(Notice(R.string.notice_recovery_failed))
        } catch (_: SecurityException) {
            notify(Notice(R.string.notice_recovery_failed))
        } finally {
            importing.value = false
        }
    }

    fun restoreRecovery() = launchWrite {
        try {
            step.value = ImportStep.Confirm(container.backupSafety.readRecovery())
        } catch (_: IOException) {
            notify(Notice(R.string.notice_recovery_unreadable))
        }
    }

    fun cancelImport() {
        if (!importing.value) step.value = null
    }

    fun noFilePicker() = notify(Notice(R.string.notice_no_file_picker))

    /** Removes the empty file the save dialog created, where the provider allows it. */
    private suspend fun discard(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: Exception) {
                // Some providers do not delete. An empty file is harmless and imports as not a backup.
            }
        }
    }

    /** Reads at most [BackupFormat.MAX_BYTES], returning null for anything larger. */
    private fun readLimited(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return out.toString(Charsets.UTF_8.name())
            out.write(buffer, 0, read)
            if (out.size() > BackupFormat.MAX_BYTES) return null
        }
    }
}

/** The fewest characters a passphrase may have. */
internal const val MIN_PASSPHRASE_LENGTH = 8

/** Offers a passphrase, then opens the system's save dialog and writes the export there. */
@Composable
fun ExportDialog(viewModel: DataViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_TYPE)) { uri ->
        if (uri != null) viewModel.export(uri) else viewModel.cancelExport()
    }
    val open by viewModel.choosingExport.collectAsStateWithLifecycle()
    if (!open) return
    // Plain remember, not saveable: a passphrase never goes into saved instance state.
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = repeat.isNotEmpty() && repeat != passphrase
    val ready = passphrase.isEmpty() || (!tooShort && repeat == passphrase)
    val export = {
        viewModel.chooseExport(passphrase)
        try {
            launcher.launch(viewModel.suggestedFileName())
        } catch (_: ActivityNotFoundException) {
            viewModel.cancelExport()
            viewModel.noFilePicker()
        }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissExport,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_dialog_body))
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.passphrase_optional),
                    error = if (tooShort) stringResource(R.string.passphrase_too_short) else null,
                    imeAction = if (passphrase.isEmpty()) ImeAction.Done else ImeAction.Next,
                    onDone = { if (ready) export() },
                )
                if (passphrase.isNotEmpty()) {
                    PassphraseField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = stringResource(R.string.passphrase_repeat),
                        error = if (mismatch) stringResource(R.string.passphrase_mismatch) else null,
                        imeAction = ImeAction.Done,
                        onDone = { if (ready) export() },
                    )
                    Text(
                        stringResource(R.string.export_passphrase_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = export, enabled = ready) { Text(stringResource(R.string.action_export)) }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissExport) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Opens the system's file picker and reads the chosen file, asking before anything changes. */
@Composable
fun rememberImport(viewModel: DataViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::read)
    }
    return {
        try {
            launcher.launch(IMPORT_TYPES)
        } catch (_: ActivityNotFoundException) {
            viewModel.noFilePicker()
        }
    }
}

/** Asks for a protected file's passphrase, then confirms the import. */
@Composable
fun ImportDialog(viewModel: DataViewModel, replacing: Boolean) {
    val current by viewModel.importStep.collectAsStateWithLifecycle()
    val exporting by viewModel.choosingExport.collectAsStateWithLifecycle()
    if (exporting) return
    when (val step = current) {
        is ImportStep.Locked -> UnlockDialog(step, viewModel)
        is ImportStep.Confirm -> ConfirmImportDialog(step.snapshot, viewModel, replacing)
        null -> Unit
    }
}

@Composable
private fun UnlockDialog(step: ImportStep.Locked, viewModel: DataViewModel) {
    var passphrase by remember(step.file) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val unlock = { if (passphrase.isNotEmpty()) viewModel.unlock(passphrase) }
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        title = { Text(stringResource(R.string.unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.unlock_body))
                PassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.passphrase_label),
                    error = if (step.wrongPassphrase) stringResource(R.string.passphrase_wrong) else null,
                    imeAction = ImeAction.Done,
                    onDone = unlock,
                    enabled = !step.unlocking,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = unlock, enabled = passphrase.isNotEmpty() && !step.unlocking) {
                Text(stringResource(if (step.unlocking) R.string.unlocking else R.string.action_unlock))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Confirms an import, saying what the file holds and, when [replacing], what will be lost. */
@Composable
private fun ConfirmImportDialog(pending: Snapshot, viewModel: DataViewModel, replacing: Boolean) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val busy by viewModel.importBusy.collectAsStateWithLifecycle()
    val savedOn = shortDate(pending.savedAt.toLocalDate(), today)
    val weeksPlanned = pending.commitments.map { it.weekStart }.distinct().size
    val closed = pending.reviews.size
    val waiting = pending.someday.count { it.letGoAt == null }
    AlertDialog(
        onDismissRequest = viewModel::cancelImport,
        title = { Text(stringResource(if (replacing) R.string.import_replace_title else R.string.import_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (replacing) {
                        stringResource(R.string.import_replace_body, savedOn)
                    } else {
                        stringResource(R.string.import_restore_body, savedOn)
                    },
                )
                Text(
                    stringResource(
                        R.string.import_contents,
                        pluralString(R.plurals.import_weeks_planned, weeksPlanned, formatCount(weeksPlanned)),
                        pluralString(R.plurals.import_weeks_closed, closed, formatCount(closed)),
                        pluralString(R.plurals.import_someday, waiting, formatCount(waiting)),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (replacing) {
                    Text(
                        stringResource(R.string.import_recovery_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::startExport, enabled = !busy) {
                        Text(stringResource(R.string.action_export_first))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmImport, enabled = !busy) {
                Text(stringResource(if (busy) R.string.importing else if (replacing) R.string.action_replace else R.string.action_restore))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::cancelImport, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A hidden text field with a show button, for passphrases. */
@Composable
internal fun PassphraseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.a11y_hide_passphrase else R.string.a11y_show_passphrase),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** A tappable row in Settings for one data action. [problem] shows the body as something to fix. */
@Composable
fun DataAction(icon: ImageVector, title: String, body: String, onClick: () -> Unit, problem: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
