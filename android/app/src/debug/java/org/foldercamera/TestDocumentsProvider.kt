package org.foldercamera

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

/** Debug-only provider for deterministic SAF failure/recovery tests. Never included in release. */
class TestDocumentsProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".testdocs"
        @Volatile var denyAccess = false
        @Volatile var denyWrites = false
        @Volatile var denyReads = false
        val columns = arrayOf("document_id", DocumentsContract.Document.COLUMN_DISPLAY_NAME, "mime_type", "flags", DocumentsContract.Document.COLUMN_SIZE, "last_modified")
    }
    private fun root() = File(context!!.filesDir, "test-documents").apply { mkdirs() }
    private fun file(id: String): File {
        if (denyAccess) throw SecurityException("test revoked")
        val root = root(); val f = if (id == "root") root else File(root, id.removePrefix("root/"))
        check(f.canonicalPath == root.canonicalPath || f.canonicalPath.startsWith(root.canonicalPath + "/"))
        return f
    }
    private fun row(cursor: MatrixCursor, f: File, id: String) {
        val values = mapOf<String, Any>("document_id" to id, DocumentsContract.Document.COLUMN_DISPLAY_NAME to if (id == "root") "Test Captures" else f.name, "mime_type" to if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "image/jpeg", "flags" to (if (f.isDirectory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else DocumentsContract.Document.FLAG_SUPPORTS_WRITE), DocumentsContract.Document.COLUMN_SIZE to f.length(), "last_modified" to f.lastModified())
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id", "document_id", "title", "flags", "available_bytes")).apply { addRow(arrayOf<Any>("test", "root", "Test Captures", DocumentsContract.Root.FLAG_SUPPORTS_CREATE, Long.MAX_VALUE)) }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(projection ?: columns).apply { val f = file(documentId); if (f.exists()) row(this, f, documentId) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(projection ?: columns).apply { file(parentDocumentId).listFiles()?.forEach { row(this, it, "$parentDocumentId/${it.name}") } }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (denyWrites) throw java.io.IOException("test full")
        val parent = file(parentDocumentId); val f = File(parent, displayName); check(!f.exists())
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) check(f.mkdir()) else check(f.createNewFile())
        return "$parentDocumentId/$displayName"
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode.contains('w') && denyWrites || !mode.contains('w') && denyReads) throw java.io.FileNotFoundException("test unavailable")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
    override fun deleteDocument(documentId: String) { check(file(documentId).delete()) }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
}
