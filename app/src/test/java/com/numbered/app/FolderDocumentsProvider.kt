package com.numbered.app

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import org.robolectric.Robolectric

/**
 * A document provider over a plain directory, standing in for whatever folder a person picks in the
 * system's folder picker, so tests drive the same DocumentsContract calls a phone would.
 */
class FolderDocumentsProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, ROOT_ID).add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return MatrixCursor(projection ?: COLUMNS).apply { add(documentId, file) }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val directory = fileOf(parentDocumentId)
        if (!directory.isDirectory) throw FileNotFoundException(parentDocumentId)
        return MatrixCursor(projection ?: COLUMNS).apply {
            directory.listFiles().orEmpty().sortedBy { it.name }.forEach { add("$parentDocumentId/${it.name}", it) }
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val directory = fileOf(parentDocumentId)
        if (directory in readOnly) throw UnsupportedOperationException("Read-only folder")
        if (!directory.isDirectory || !File(directory, displayName).createNewFile()) throw FileNotFoundException(displayName)
        return "$parentDocumentId/$displayName"
    }

    override fun deleteDocument(documentId: String) {
        if (!fileOf(documentId).delete()) throw FileNotFoundException(documentId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")

    private fun MatrixCursor.add(documentId: String, file: File) {
        newRow()
            .add(Document.COLUMN_DOCUMENT_ID, documentId)
            .add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "application/json")
            .add(Document.COLUMN_FLAGS, if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_DELETE)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun fileOf(documentId: String): File =
        if (documentId == ROOT_ID) root else File(root, documentId.removePrefix("$ROOT_ID/"))

    companion object {
        const val AUTHORITY = "com.numbered.app.test.documents"
        private const val ROOT_ID = "root"
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )

        lateinit var root: File

        /** Folders that refuse new files, like a read-only memory card. */
        val readOnly = mutableSetOf<File>()

        /** Serves [directory] as a folder the person picked, and returns its tree URI. */
        fun register(directory: File): Uri {
            root = directory
            readOnly.clear()
            Robolectric.buildContentProvider(FolderDocumentsProvider::class.java).create(
                ProviderInfo().apply {
                    authority = AUTHORITY
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                },
            )
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
        }

        /** The tree URI for a folder inside the registered one, as if picked separately. */
        fun treeOf(name: String): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "$ROOT_ID/$name")
    }
}
