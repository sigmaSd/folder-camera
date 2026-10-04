package org.foldercamera.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.foldercamera.FolderCameraApp
import org.foldercamera.core.*
import org.foldercamera.data.*

class CameraModel(application: Application) : AndroidViewModel(application) {
    val app = application as FolderCameraApp
    val gate = DestinationGate()
    var screen by mutableStateOf("destination")
    var path by mutableStateOf(app.config.lastPath)
    var root by mutableStateOf(app.config.root)
    var sync by mutableStateOf(app.config.syncEnabled)
    var receiver by mutableStateOf(runCatching { app.config.receiver() }.getOrNull())
    var error by mutableStateOf<String?>(null)
    var lightMode by mutableStateOf(LightMode.OFF)
    var zoomRatio by mutableFloatStateOf(1f)
    var folderPaths by mutableStateOf<List<String>>(emptyList())
    var folderLoading by mutableStateOf(false)
    var folderError by mutableStateOf(false)
    var rootName by mutableStateOf<String?>(null)
    private var folderJob: Job? = null
    private var folderGeneration = 0
    fun refreshFolders() {
        val selected = root ?: return
        folderJob?.cancel(); val generation = ++folderGeneration; folderLoading = true; folderError = false
        folderJob = viewModelScope.launch {
            try {
                val (name, paths) = org.foldercamera.storage.FolderCatalog(app).scan(selected) { paths ->
                    withContext(Dispatchers.Main) { if (root == selected && generation == folderGeneration) folderPaths = paths }
                }
                if (root == selected && generation == folderGeneration) { rootName = name; folderPaths = paths }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { if (root == selected && generation == folderGeneration) folderError = true }
            finally { if (root == selected && generation == folderGeneration) folderLoading = false }
        }
    }
    var busy by mutableStateOf(false)
    val captures = app.db.dao().observeCaptures().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deliveries = app.db.dao().observeDeliveries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun confirm() {
        error = PortablePath.validate(path)
        if (error == null && root != null) { gate.confirm(root!!, path); app.config.lastPath = path; screen = "camera" }
    }
    fun back() { screen = if (gate.session != null) "camera" else "destination" }
    fun selectRoot(uri: String) {
        root = uri; folderPaths = emptyList(); rootName = null; app.config.root = uri; gate.reset(); screen = "destination"; error = null; refreshFolders()
    }
    fun retryLocal() { app.scope.launch { app.store.reconcile(); app.delivery.kick() } }
    fun toggleSync(value: Boolean) { sync = value; app.config.syncEnabled = value; if (value) app.delivery.kick() else app.delivery.pause() }
    fun retryNetwork() { app.scope.launch { receiver?.let { app.db.dao().retry(it.id) }; app.delivery.kick() } }
    fun importFolders(paths: Set<String>) { app.scope.launch {
        val r = receiver ?: return@launch
        app.db.dao().saved().filter { it.relativePath in paths }.forEach { app.db.dao().enqueue(Delivery(it.photoId, r.id)) }
        app.delivery.kick()
    } }
    fun capture(take: (java.io.File, (Boolean) -> Unit) -> Unit) {
        val session = gate.session ?: return
        if (busy) return
        busy = true
        val boundReceiver = if (sync) receiver?.id else null
        viewModelScope.launch {
            try {
                val c = app.store.begin(session.root, session.path, boundReceiver)
                take(java.io.File(c.stagingPath!!)) { success ->
                    busy = false
                    if (!success) error = "capture_failed"
                    app.scope.launch { app.store.persist(c.photoId); app.delivery.kick() }
                }
            } catch (_: Exception) { busy = false; error = "storage_unavailable" }
        }
    }
}
