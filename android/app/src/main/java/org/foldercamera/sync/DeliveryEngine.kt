package org.foldercamera.sync

import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.foldercamera.FolderCameraApp
import org.foldercamera.core.DeliveryPolicy
import java.util.concurrent.TimeUnit

interface DeliveryQueue { suspend fun drain(); fun kick(); fun pause() }
class DeliveryEngine(private val context: Context) : DeliveryQueue {
    private val app get() = context.applicationContext as FolderCameraApp
    private val mutex = Mutex()
    private var active: Job? = null
    fun allowed(): Boolean = app.config.syncEnabled && (Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED)
    override fun kick() {
        if (!allowed()) return
        synchronized(this) {
            if (active?.isActive != true) active = app.scope.launch { drain() }
        }
        // CONNECTED can require validated internet on older Android. LAN retries must also run offline.
        val request = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("delivery", ExistingWorkPolicy.KEEP, request)
    }
    override fun pause() { WorkManager.getInstance(context).cancelUniqueWork("delivery") }
    override suspend fun drain() = mutex.withLock {
        while (allowed()) {
            val receiver = runCatching { app.config.receiver() }.getOrNull() ?: return@withLock
            val d = app.db.dao().claim(receiver.id, System.currentTimeMillis()) ?: return@withLock
            try {
                val c = app.db.dao().capture(d.photoId) ?: throw ProtocolFailure("local_missing", true)
                if (c.state != "SAVED") throw ProtocolFailure("local_not_saved", true)
                // Blocking network is deliberately confined to IO; cancellation never starts another request.
                val receipt = withContext(Dispatchers.IO) { PinnedTransport(context).upload(c, receiver) }
                app.db.dao().finish(d.photoId, receiver.id, d.claim!!, "RECEIVED", 0, null, receipt)
            } catch (e: Exception) {
                val permanent = e is ProtocolFailure && e.permanent || e is javax.net.ssl.SSLException || e is SecurityException
                val code = (e as? ProtocolFailure)?.code ?: if (e is javax.net.ssl.SSLException) "certificate_changed" else if (e is SecurityException) "local_permission" else "connection_unavailable"
                app.db.dao().finish(d.photoId, receiver.id, d.claim!!, if (permanent) "FAILED" else "PENDING", System.currentTimeMillis() + DeliveryPolicy.retryDelay(d.attempts), code, null)
                if (!permanent) return@withLock
            }
        }
    }
}
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FolderCameraApp
        if (!app.delivery.allowed()) return Result.success()
        app.delivery.drain()
        val receiver = runCatching { app.config.receiver() }.getOrNull() ?: return Result.success()
        return if (app.db.dao().eligible(receiver.id) > 0) Result.retry() else Result.success()
    }
}
