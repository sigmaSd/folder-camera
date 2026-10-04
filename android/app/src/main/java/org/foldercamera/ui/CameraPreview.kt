package org.foldercamera.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.util.concurrent.Executors

enum class LightMode { OFF, AUTO, FLASH, TORCH }
data class CameraHandle(val capture: ImageCapture, val camera: Camera)

@Composable
fun CameraPreview(
    modifier: Modifier,
    lightMode: LightMode = LightMode.OFF,
    zoomRatio: Float = 1f,
    onReady: (CameraHandle?) -> Unit,
    onFailure: () -> Unit,
    onQr: ((String) -> Unit)? = null,
) {
    val owner = LocalLifecycleOwner.current
    val qrCallback by rememberUpdatedState(onQr)
    val readyCallback by rememberUpdatedState(onReady)
    val failureCallback by rememberUpdatedState(onFailure)
    val executor = remember { Executors.newSingleThreadExecutor() }
    var handle by remember { mutableStateOf<CameraHandle?>(null) }
    val capture = remember { ImageCapture.Builder().build() }
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeCount++
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && handle?.camera?.cameraInfo?.hasFlashUnit() == true) handle?.camera?.cameraControl?.enableTorch(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(handle, lightMode, resumeCount) {
        val current = handle ?: return@LaunchedEffect
        capture.flashMode = when (lightMode) { LightMode.AUTO -> ImageCapture.FLASH_MODE_AUTO; LightMode.FLASH -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
        if (current.camera.cameraInfo.hasFlashUnit()) {
            val result = current.camera.cameraControl.enableTorch(lightMode == LightMode.TORCH)
            result.addListener({ try { result.get() } catch (e: Exception) { if (handle === current && e.cause !is CameraControl.OperationCanceledException && e !is java.util.concurrent.CancellationException) failureCallback() } }, ContextCompat.getMainExecutor(context))
        }
    }
    LaunchedEffect(handle, zoomRatio) {
        val current = handle ?: return@LaunchedEffect
        val zoom = current.camera.cameraInfo.zoomState.value ?: return@LaunchedEffect
        val result = current.camera.cameraControl.setZoomRatio(zoomRatio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
        result.addListener({ try { result.get() } catch (_: CameraControl.OperationCanceledException) { /* Replaced by a newer gesture. */ } catch (e: Exception) { if (handle === current && e.cause !is CameraControl.OperationCanceledException && e !is java.util.concurrent.CancellationException) failureCallback() } }, ContextCompat.getMainExecutor(context))
    }
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(owner, view) {
        var provider: ProcessCameraProvider? = null
        var boundCamera: Camera? = null
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(listener@{
            if (!active.get()) return@listener
            try {
                val p = future.get(); provider = p
                if (qrCallback != null) analysis.setAnalyzer(executor) { proxy ->
                    try {
                        val plane = proxy.planes[0]; val buffer = plane.buffer
                        val bytes = ByteArray(proxy.width * proxy.height)
                        for (y in 0 until proxy.height) for (x in 0 until proxy.width) bytes[y * proxy.width + x] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
                        val source = PlanarYUVLuminanceSource(bytes, proxy.width, proxy.height, 0, 0, proxy.width, proxy.height, false)
                        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
                        val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                        if (result.length <= 4096) ContextCompat.getMainExecutor(context).execute { if (active.get()) qrCallback?.invoke(result) }
                    } catch (_: Exception) { /* No QR in this frame. */ } finally { proxy.close() }
                }
                val camera = if (qrCallback != null) p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    else p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                boundCamera = camera
                handle = CameraHandle(capture, camera); readyCallback(handle)
            } catch (_: Exception) { failureCallback() }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            active.set(false)
            if (boundCamera?.cameraInfo?.hasFlashUnit() == true) boundCamera?.cameraControl?.enableTorch(false)
            provider?.unbind(preview, capture, analysis)
            analysis.clearAnalyzer(); executor.shutdown(); handle = null; readyCallback(null)
        }
    }
    AndroidView(modifier = modifier, factory = { view })
}
fun takePhoto(context: Context, capture: ImageCapture, file: File, complete: (Boolean) -> Unit) {
    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) { complete(true) }
        override fun onError(exception: ImageCaptureException) { complete(false) }
    })
}
