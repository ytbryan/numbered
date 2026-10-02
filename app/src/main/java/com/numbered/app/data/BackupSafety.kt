package com.numbered.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** One private recovery copy, excluded from cloud backups, plus the last successful export date. */
class BackupSafety(context: Context) {
    private val preferences = context.getSharedPreferences("backup_status", Context.MODE_PRIVATE)
    private val recovery = AtomicFile(File(context.noBackupFilesDir, "before-import.json"))
    private val exported = MutableStateFlow(preferences.getLong("last_export", 0).takeIf { it > 0 })
    private val recovered = MutableStateFlow(recovery.baseFile.lastModified().takeIf { it > 0 })
    val lastExport = exported.asStateFlow()
    val recoveryDate = recovered.asStateFlow()

    fun recordExport(at: Long) {
        preferences.edit { putLong("last_export", at) }
        exported.value = at
    }

    /** A failed or interrupted write leaves the previous recovery copy intact. */
    suspend fun saveRecovery(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val bytes = BackupFormat.encode(snapshot).toByteArray(Charsets.UTF_8)
        val stream = recovery.startWrite()
        try {
            stream.write(bytes)
            recovery.finishWrite(stream)
        } catch (failure: Exception) {
            recovery.failWrite(stream)
            throw failure
        }
        recovered.value = snapshot.savedAt
    }

    suspend fun readRecovery(): Snapshot = withContext(Dispatchers.IO) {
        val result = BackupFormat.decode(recovery.openRead().bufferedReader().use { it.readText() })
        (result as? BackupRead.Ok)?.snapshot ?: throw IOException("Unreadable recovery copy")
    }
}
