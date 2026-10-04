package com.numbered.app.backup

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** A folder the person chose for automatic copies. Every call may throw [IOException] or [SecurityException]. */
interface BackupFolder {
    /** The names of the files directly inside. */
    fun list(): List<String>

    /** Creates a file called [name] holding [bytes], and checks it reads back the same. */
    fun create(name: String, bytes: ByteArray)

    fun delete(name: String)

    /** What the folder is called, for showing where copies go. */
    fun displayName(): String?
}

/**
 * A folder granted through the system's folder picker. It may be on this phone, a memory card, or a
 * cloud provider, so it can vanish, and the grant can be withdrawn, at any time.
 */
class DocumentTreeFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val treeId = DocumentsContract.getTreeDocumentId(tree)

    override fun list(): List<String> = children().keys.toList()

    override fun create(name: String, bytes: ByteArray) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val created = DocumentsContract.createDocument(resolver, parent, MIME_TYPE, name)
            ?: throw IOException("The folder refused a new file")
        try {
            val output = resolver.openOutputStream(created, "wt") ?: throw IOException("No stream for $created")
            output.use { it.write(bytes) }
            // Cloud and memory card providers can accept a write and still lose it, so read it back.
            val written = resolver.openInputStream(created)?.use { it.readBytes() }
            if (!bytes.contentEquals(written)) throw IOException("The copy did not read back the same")
        } catch (failure: Exception) {
            try {
                DocumentsContract.deleteDocument(resolver, created)
            } catch (_: Exception) {
                // An incomplete file is left behind only if the folder also refuses to delete it.
            }
            throw failure
        }
    }

    override fun delete(name: String) {
        val id = children()[name] ?: return
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
    }

    override fun displayName(): String? = resolver.query(
        DocumentsContract.buildDocumentUriUsingTree(tree, treeId),
        arrayOf(Document.COLUMN_DISPLAY_NAME),
        null as Bundle?,
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** File names to document IDs. */
    private fun children(): Map<String, String> {
        val cursor = resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
            null as Bundle?,
            null,
        ) ?: throw IOException("The folder cannot be read")
        return cursor.use {
            buildMap {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    put(name, it.getString(0))
                }
            }
        }
    }

    private companion object {
        const val MIME_TYPE = "application/json"
    }
}
