package org.foldercamera.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.foldercamera.core.PortablePath

/** One query per directory, with all file rows skipped; no per-photo metadata calls. */
class FolderCatalog(private val context: Context) {
    suspend fun scan(tree: String, onProgress: suspend (List<String>) -> Unit): Pair<String?, List<String>> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(tree)
        val rootId = DocumentsContract.getTreeDocumentId(uri)
        val queue = ArrayDeque<Pair<String, String>>().apply { add(rootId to "") }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<String>()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val name = DocumentFile.fromTreeUri(context, uri)?.name
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, parent) = queue.removeFirst()
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
            val cursor = context.contentResolver.query(children, projection, null, null, null) ?: error("storage_permission")
            cursor.use {
                val idIndex = it.getColumnIndexOrThrow(projection[0]); val nameIndex = it.getColumnIndexOrThrow(projection[1]); val mimeIndex = it.getColumnIndexOrThrow(projection[2])
                while (it.moveToNext()) {
                    if (it.getString(mimeIndex) != DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val segment = it.getString(nameIndex) ?: continue
                    val path = if (parent.isEmpty()) segment else "$parent/$segment"
                    if (PortablePath.validate(path) != null) continue
                    result.add(path)
                    if (path.count { ch -> ch == '/' } < 31) queue.add(it.getString(idIndex) to path)
                }
            }
            if (visited.size % 20 == 0) onProgress(result.toList())
        }
        name to result
    }
}
