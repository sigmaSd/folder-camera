package org.foldercamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.foldercamera.ui.CameraModel
import org.foldercamera.ui.FolderCameraUi

class MainActivity : ComponentActivity() {
    private val model by viewModels<CameraModel>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FolderCameraUi(model) } }
    override fun onResume() { super.onResume(); (application as FolderCameraApp).delivery.kick() }
}
