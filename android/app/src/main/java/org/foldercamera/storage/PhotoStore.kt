package org.foldercamera.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.foldercamera.core.PortablePath
import org.foldercamera.data.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

interface PhotoStorage { suspend fun persist(id: String); suspend fun reconcile() }
fun digest(input: InputStream): Pair<Long, String> {
    val hash = MessageDigest.getInstance("SHA-256"); var size = 0L
    input.use { stream -> val buffer = ByteArray(64 * 1024); while (true) {
        val n = stream.read(buffer); if (n < 0) break; hash.update(buffer, 0, n); size += n
    } }
    return size to hash.digest().joinToString("") { "%02x".format(it) }
}
class PhotoStore(private val context: Context, private val dao: CameraDao) : PhotoStorage {
    private val mutex = Mutex()
    private val activeCaptures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    suspend fun begin(root: String, path: String, receiverId: String?): Capture = withContext(Dispatchers.IO) {
        require(PortablePath.validate(path) == null)
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        val filename = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date(now)) + "_" + id.replace("-", "") + ".jpg"
        val staging = File(context.filesDir, "staging").apply { mkdirs() }
        Capture(id, filename, now, path, root, stagingPath = File(staging, "$id.jpg").path, receiverId = receiverId).also { dao.insert(it); activeCaptures.add(it.photoId) }
    }
    override suspend fun persist(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        activeCaptures.remove(id)
        var c = dao.capture(id) ?: return@withLock
        if (c.state == "SAVED") { cleanup(c); return@withLock }
        try {
            val stage = c.stagingPath?.let(::File)
            if (c.sha256.isEmpty()) {
                check(stage != null && validJpeg(stage)) { "capture_interrupted" }
                val (size, hash) = digest(stage.inputStream())
                c = c.copy(byteSize = size, sha256 = hash, state = "STAGED", lastLocalError = null); dao.update(c)
            }
            // An acknowledged or interrupted destination may already contain the complete bytes.
            c.destinationDocumentUri?.let { existing ->
                val actual = runCatching { digest(context.contentResolver.openInputStream(Uri.parse(existing)) ?: error("storage_read")) }.getOrNull()
                if (actual == c.byteSize to c.sha256) { dao.markSaved(c); cleanup(c); return@withLock }
            }
            check(stage != null && stage.exists()) { "staging_missing" }
            val actualStage = digest(stage.inputStream())
            check(actualStage == c.byteSize to c.sha256) { "staging_corrupt" }
            var destination = c.destinationDocumentUri?.let { DocumentFile.fromSingleUri(context, Uri.parse(it)) }
            if (destination == null || !destination.exists()) {
                val parent = directory(c.baseTreeUri, c.relativePath)
                val existing = parent.listFiles().firstOrNull { sameName(it.name, c.filename) }
                if (existing != null) {
                    // Creation can succeed before URI journaling. Adopt only exact, fully verified bytes.
                    check(c.creationStarted && existing.name == c.filename && digest(context.contentResolver.openInputStream(existing.uri) ?: error("storage_read")) == c.byteSize to c.sha256) { "storage_conflict" }
                    c = c.copy(destinationDocumentUri = existing.uri.toString()); dao.markSaved(c); cleanup(c); return@withLock
                }
                c = c.copy(state = "COPYING", creationStarted = true, destinationDocumentUri = null); dao.update(c)
                destination = parent.createFile("image/jpeg", c.filename) ?: error("storage_create")
                if (destination.name != c.filename) { destination.delete(); error("storage_renamed") }
                c = c.copy(destinationDocumentUri = destination.uri.toString()); dao.update(c)
            }
            val output = context.contentResolver.openOutputStream(destination.uri, "wt") ?: error("storage_write")
            output.use { out -> stage.inputStream().use { it.copyTo(out, 64 * 1024) }; out.flush() }
            check(digest(context.contentResolver.openInputStream(destination.uri) ?: error("storage_read")) == c.byteSize to c.sha256) { "storage_verify" }
            dao.markSaved(c); cleanup(c)
        } catch (e: Exception) {
            // Provider exception text may contain private paths; surface a stable, actionable category.
            val reason = if (e is SecurityException) "storage_permission" else e.message?.takeIf { it.startsWith("storage_") || it.startsWith("staging_") || it == "capture_interrupted" } ?: "storage_unavailable"
            dao.update(c.copy(state = "LOCAL_FAILED", lastLocalError = reason))
        }
    } }
    private suspend fun cleanup(c: Capture) {
        c.stagingPath?.let { if (File(it).delete() || !File(it).exists()) dao.update(c.copy(state = "SAVED", stagingPath = null, lastLocalError = null)) }
    }
    private fun validJpeg(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        java.io.RandomAccessFile(file, "r").use {
            if (it.readUnsignedShort() != 0xffd8) return false
            it.seek(file.length() - 2); if (it.readUnsignedShort() != 0xffd9) return false
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return opts.outWidth > 0 && opts.outHeight > 0
    }
    private fun sameName(a: String?, b: String): Boolean = a != null && java.text.Normalizer.normalize(a, java.text.Normalizer.Form.NFC).lowercase(Locale.ROOT) == java.text.Normalizer.normalize(b, java.text.Normalizer.Form.NFC).lowercase(Locale.ROOT)
    fun directory(tree: String, path: String): DocumentFile {
        require(PortablePath.validate(path) == null)
        var current = DocumentFile.fromTreeUri(context, Uri.parse(tree)) ?: error("storage_permission")
        check(current.canWrite()) { "storage_permission" }
        for (segment in path.split('/')) {
            val matches = current.listFiles().filter { sameName(it.name, segment) }
            check(matches.size <= 1) { "storage_conflict" }
            current = if (matches.isEmpty()) current.createDirectory(segment)?.also {
                if (it.name != segment) { it.delete(); error("storage_renamed") }
            } ?: error("storage_create") else matches.single().also { check(it.name == segment && it.isDirectory) { "storage_conflict" } }
        }
        return current
    }
    override suspend fun reconcile() {
        dao.incomplete().filter { it.photoId !in activeCaptures }.forEach { persist(it.photoId) }
        dao.saved().filter { it.stagingPath != null }.forEach { cleanup(it) }
    }
}
