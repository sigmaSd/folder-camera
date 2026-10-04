package org.foldercamera

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.room.Room
import kotlinx.coroutines.*
import org.foldercamera.data.*
import org.foldercamera.storage.PhotoStore
import org.foldercamera.sync.DeliveryEngine

class FolderCameraApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var db: CameraDatabase; private set
    lateinit var config: Configuration; private set
    lateinit var store: PhotoStore; private set
    lateinit var delivery: DeliveryEngine; private set
    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, CameraDatabase::class.java, "camera.db").build()
        config = Configuration(this); store = PhotoStore(this, db.dao()); delivery = DeliveryEngine(this)
        scope.launch { store.reconcile(); delivery.kick() }
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
            android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).build(),
            object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { if (config.syncEnabled) scope.launch { db.dao().reconnect(); delivery.kick() } } })
    }
}
