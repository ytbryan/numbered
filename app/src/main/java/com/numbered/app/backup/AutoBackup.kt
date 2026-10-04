package com.numbered.app.backup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.numbered.app.MainActivity
import com.numbered.app.R
import com.numbered.app.appContainer
import com.numbered.app.data.BackupFormat
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.Snapshot
import com.numbered.app.security.PassphraseVault
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.ProviderException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why automatic copies are not being saved. */
enum class AutoBackupProblem {
    /** The folder is gone, unreachable, or no longer granted. */
    FolderUnavailable,

    /** The passphrase kept on this phone can no longer be opened. */
    PassphraseUnavailable,
}

/** What the person sees about automatic copies. Off when [folder] is null. */
data class AutoBackupStatus(
    val folder: Uri? = null,
    val folderName: String = "",
    val protected: Boolean = false,
    /** The last time the folder was confirmed to hold a current copy. */
    val lastSavedAt: Long? = null,
    val problem: AutoBackupProblem? = null,
) {
    val enabled: Boolean get() = folder != null
}

enum class AutoBackupResult { Saved, Unchanged, Off, NotSetUp, FolderUnavailable, PassphraseUnavailable }

/**
 * Which automatic copies to keep: the newest few, and the newest of each recent month, so a
 * mistake that goes unnoticed for weeks still leaves an older good copy. Only files this app
 * named are ever counted or deleted.
 */
internal object Retention {
    const val RECENT = 7
    const val MONTHS = 12

    private val NAME = Regex("""numbered-auto-(\d{4}-\d{2}-\d{2}-\d{6})\.json""")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    fun fileName(at: LocalDateTime): String = "numbered-auto-${at.format(STAMP)}.json"

    fun parse(name: String): LocalDateTime? = NAME.matchEntire(name)?.let {
        runCatching { LocalDateTime.parse(it.groupValues[1], STAMP) }.getOrNull()
    }

    fun toDelete(names: Collection<String>): List<String> {
        val ours = names.distinct().mapNotNull { name -> parse(name)?.let { name to it } }.sortedByDescending { it.second }
        val keep = ours.take(RECENT).map { it.first }.toMutableSet()
        ours.groupBy { YearMonth.from(it.second) }
            .entries.sortedByDescending { it.key }
            .take(MONTHS)
            .forEach { (_, inMonth) -> keep += inMonth.first().first }
        return ours.map { it.first }.filterNot { it in keep }
    }
}

/**
 * Device preferences, never in exports or device backups: a folder grant and a Keystore-sealed
 * passphrase mean nothing on another phone.
 */
internal class AutoBackupStore(context: Context) {
    private val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val status: StateFlow<AutoBackupStatus> = state.asStateFlow()

    val sealedPassphrase: String? get() = prefs.getString(PASSPHRASE, null)
    val lastHash: String? get() = prefs.getString(LAST_HASH, null)
    val lastFile: String? get() = prefs.getString(LAST_FILE, null)

    /** Starts afresh with [folder], already holding the copy [file] saved [at]. */
    fun configure(folder: Uri, folderName: String, sealedPassphrase: String?, at: Long, hash: String, file: String) = write {
        clear()
        putString(FOLDER, folder.toString())
        putString(FOLDER_NAME, folderName)
        putString(PASSPHRASE, sealedPassphrase)
        putSaved(at, hash, file)
    }

    fun recordSaved(at: Long, hash: String, file: String, folderName: String) = write {
        putSaved(at, hash, file)
        putString(FOLDER_NAME, folderName)
    }

    fun recordChecked(at: Long, folderName: String) = write {
        putLong(LAST_SAVED, at)
        clearProblem()
        putString(FOLDER_NAME, folderName)
    }

    /** Records [problem], returning true once per run of failures, when it has lasted [notifyAfter]. */
    fun recordProblem(problem: AutoBackupProblem, at: Long, notifyAfter: Duration): Boolean {
        val since = prefs.getLong(FAILING_SINCE, 0).takeIf { it > 0 } ?: at
        val due = !prefs.getBoolean(NOTIFIED, false) && at - since >= notifyAfter.toMillis()
        write {
            putString(PROBLEM, problem.name)
            putLong(FAILING_SINCE, since)
            if (due) putBoolean(NOTIFIED, true)
        }
        return due
    }

    fun clear() = write { clear() }

    private fun SharedPreferences.Editor.putSaved(at: Long, hash: String, file: String) {
        putLong(LAST_SAVED, at)
        putString(LAST_HASH, hash)
        putString(LAST_FILE, file)
        clearProblem()
    }

    private fun SharedPreferences.Editor.clearProblem() {
        remove(PROBLEM)
        remove(FAILING_SINCE)
        remove(NOTIFIED)
    }

    // Committed synchronously: a backup must never be recorded as saved unless that is stored.
    private fun write(change: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = change)
        state.value = read()
    }

    private fun read() = AutoBackupStatus(
        folder = prefs.getString(FOLDER, null)?.toUri(),
        folderName = prefs.getString(FOLDER_NAME, null).orEmpty(),
        protected = prefs.getString(PASSPHRASE, null) != null,
        lastSavedAt = prefs.getLong(LAST_SAVED, 0).takeIf { it > 0 },
        problem = prefs.getString(PROBLEM, null)?.let { name -> AutoBackupProblem.entries.firstOrNull { it.name == name } },
    )

    private companion object {
        const val FOLDER = "folder"
        const val FOLDER_NAME = "folder_name"
        const val PASSPHRASE = "passphrase"
        const val LAST_SAVED = "last_saved"
        const val LAST_HASH = "last_hash"
        const val LAST_FILE = "last_file"
        const val PROBLEM = "problem"
        const val FAILING_SINCE = "failing_since"
        const val NOTIFIED = "notified"
    }
}

/**
 * Saves a full copy, like Export, to a folder the person chose, once a day and only when something
 * changed. Device backups depend on the phone's account settings and exports on remembering, so
 * this is what keeps a lifetime of weeks safe if the phone is lost.
 */
class AutoBackup(
    private val context: Context,
    private val repository: NumberedRepository,
    private val clock: Clock,
    private val vault: PassphraseVault,
    private val folderAt: (Uri) -> BackupFolder = { DocumentTreeFolder(context.contentResolver, it) },
) {
    private val store = AutoBackupStore(context)
    private val mutex = Mutex()
    private val busy = MutableStateFlow(false)
    private val notifications = NotificationManagerCompat.from(context)

    val status: StateFlow<AutoBackupStatus> = store.status

    /** Whether a copy is being saved now. */
    val running: StateFlow<Boolean> = busy.asStateFlow()

    /**
     * Turns on automatic copies to [tree], replacing any earlier folder, but only once a first copy
     * has been saved there. Otherwise nothing changes.
     */
    suspend fun enable(tree: Uri, passphrase: String?): AutoBackupResult = exclusive {
        val sealed = try {
            passphrase?.let(vault::seal)
        } catch (_: GeneralSecurityException) {
            return@exclusive AutoBackupResult.PassphraseUnavailable
        } catch (_: ProviderException) {
            return@exclusive AutoBackupResult.PassphraseUnavailable
        }
        val previous = store.status.value.folder
        try {
            context.contentResolver.takePersistableUriPermission(tree, READ_WRITE)
        } catch (_: SecurityException) {
            return@exclusive AutoBackupResult.FolderUnavailable
        }
        val snapshot = repository.snapshot() ?: return@exclusive AutoBackupResult.NotSetUp
        try {
            val folder = folderAt(tree)
            val copy = Copy(snapshot, passphrase)
            val file = save(folder, copy, folder.list())
            store.configure(tree, folder.nameOr(tree), sealed, clock.millis(), copy.hash, file)
        } catch (failure: Exception) {
            if (!failure.isFolderFailure()) throw failure
            if (tree != previous) release(tree)
            return@exclusive AutoBackupResult.FolderUnavailable
        }
        if (previous != null && previous != tree) release(previous)
        notifications.cancel(NOTIFICATION_ID)
        schedule(ExistingPeriodicWorkPolicy.UPDATE)
        AutoBackupResult.Saved
    }

    /** Saves a copy now if anything changed since the last one. */
    suspend fun run(): AutoBackupResult = exclusive {
        val tree = store.status.value.folder ?: return@exclusive AutoBackupResult.Off
        val passphrase = store.sealedPassphrase?.let { sealed ->
            vault.open(sealed) ?: return@exclusive problem(AutoBackupProblem.PassphraseUnavailable)
        }
        val snapshot = repository.snapshot() ?: return@exclusive AutoBackupResult.NotSetUp
        try {
            val folder = folderAt(tree)
            val names = folder.list()
            val copy = Copy(snapshot, passphrase)
            val result = if (copy.hash == store.lastHash && store.lastFile in names) {
                prune(folder, names)
                store.recordChecked(clock.millis(), folder.nameOr(tree))
                AutoBackupResult.Unchanged
            } else {
                val file = save(folder, copy, names)
                store.recordSaved(clock.millis(), copy.hash, file, folder.nameOr(tree))
                AutoBackupResult.Saved
            }
            notifications.cancel(NOTIFICATION_ID)
            result
        } catch (failure: Exception) {
            if (!failure.isFolderFailure()) throw failure
            problem(AutoBackupProblem.FolderUnavailable)
        }
    }

    /** Stops automatic copies. Files already saved stay in the folder. */
    suspend fun disable() = exclusive {
        store.status.value.folder?.let(::release)
        store.clear()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        notifications.cancel(NOTIFICATION_ID)
    }

    /** Schedules the daily copy again, for app start. WorkManager keeps an existing schedule. */
    fun keepScheduled() {
        if (store.status.value.enabled) schedule(ExistingPeriodicWorkPolicy.KEEP)
    }

    private suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock {
        busy.value = true
        try {
            withContext(Dispatchers.IO) { block() }
        } finally {
            busy.value = false
        }
    }

    /** Writes [copy] under a new name, then removes copies retention no longer keeps. */
    private fun save(folder: BackupFolder, copy: Copy, existing: List<String>): String {
        val name = Retention.fileName(LocalDateTime.now(clock))
        folder.create(name, copy.bytes)
        prune(folder, existing + name)
        return name
    }

    private fun prune(folder: BackupFolder, names: List<String>) {
        Retention.toDelete(names).forEach { name ->
            try {
                folder.delete(name)
            } catch (_: Exception) {
                // An old copy left behind is harmless; the next run tries again.
            }
        }
    }

    private fun problem(problem: AutoBackupProblem): AutoBackupResult {
        if (store.recordProblem(problem, clock.millis(), NOTIFY_AFTER)) notifyProblem(problem)
        return when (problem) {
            AutoBackupProblem.FolderUnavailable -> AutoBackupResult.FolderUnavailable
            AutoBackupProblem.PassphraseUnavailable -> AutoBackupResult.PassphraseUnavailable
        }
    }

    private fun notifyProblem(problem: AutoBackupProblem) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !notifications.areNotificationsEnabled()) return
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.auto_backup_channel_name))
                .setDescription(context.getString(R.string.auto_backup_channel_description))
                .build(),
        )
        val text = context.getString(
            when (problem) {
                AutoBackupProblem.FolderUnavailable -> R.string.auto_backup_notification_folder
                AutoBackupProblem.PassphraseUnavailable -> R.string.auto_backup_notification_passphrase
            },
        )
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.auto_backup_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(
                PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post.
        }
    }

    private fun release(tree: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(tree, READ_WRITE)
        } catch (_: SecurityException) {
            // Already gone.
        }
    }

    private fun schedule(policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    /**
     * One copy, with a fingerprint of its contents that ignores when it was taken. The file is only
     * encoded when it is needed, since sealing with a passphrase is slow on purpose.
     */
    private class Copy(private val snapshot: Snapshot, private val passphrase: String?) {
        val hash: String = run {
            val unchanging = BackupFormat.encode(snapshot.copy(savedAt = 0)).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(unchanging).joinToString("") { "%02x".format(it) }
            // Turning protection on or off changes the file, though not what it holds.
            if (passphrase == null) digest else "protected:$digest"
        }

        val bytes: ByteArray by lazy { BackupFormat.encode(snapshot, passphrase).toByteArray(Charsets.UTF_8) }
    }

    companion object {
        const val WORK_NAME = "auto-backup"
        private const val CHANNEL = "backups"
        private const val NOTIFICATION_ID = 3
        private const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** One bad day may be a cloud provider offline. Two in a row needs the person. */
        private val NOTIFY_AFTER: Duration = Duration.ofDays(2)

        /** Providers must name every folder, but a nameless one still needs something to show. */
        private fun BackupFolder.nameOr(tree: Uri): String =
            displayName()?.takeIf { it.isNotBlank() } ?: DocumentsContract.getTreeDocumentId(tree)

        /** Folders can disappear or refuse at any time, and providers report it in different ways. */
        private fun Exception.isFolderFailure() = this is IOException || this is SecurityException ||
            this is IllegalArgumentException || this is UnsupportedOperationException
    }
}

/** Runs the daily copy. A failure is recorded for the person to see, and tomorrow's run tries again. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.autoBackup.run()
        return Result.success()
    }
}
