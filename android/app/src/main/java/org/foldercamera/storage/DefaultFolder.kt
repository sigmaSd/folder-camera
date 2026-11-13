package org.foldercamera.storage

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** Where the system folder picker opens when no base directory is chosen yet: Pictures/FolderCamera. */
object DefaultFolder {
    private const val PROVIDER = "com.android.externalstorage.documents"
    private const val NAME = "FolderCamera"

    /**
     * Creates Pictures/FolderCamera when the platform allows it without a permission (Android 11 and later)
     * and returns its picker location. Otherwise it returns Pictures, where the user can make the folder in the picker.
     * Does disk work; call it off the main thread.
     */
    fun pickerStart(): Uri {
        val pictures = Environment.DIRECTORY_PICTURES
        val folder = File(Environment.getExternalStoragePublicDirectory(pictures), NAME)
        runCatching { folder.mkdirs() }
        val id = if (runCatching { folder.isDirectory }.getOrDefault(false)) "primary:$pictures/$NAME" else "primary:$pictures"
        return DocumentsContract.buildDocumentUri(PROVIDER, id)
    }
}
