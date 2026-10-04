package org.foldercamera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.foldercamera.R
import org.foldercamera.data.Capture
import org.foldercamera.data.Delivery
import java.util.Locale

@Composable fun CaptureScreen(model: CameraModel, captures: List<Capture>, deliveries: List<Delivery>) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var handle by remember { mutableStateOf<CameraHandle?>(null) }
    var flashMenu by remember { mutableStateOf(false) }
    var fullPath by remember { mutableStateOf(false) }
    val session = model.gate.session ?: return
    val currentPhotos = remember(captures, session) { captures.filter { it.baseTreeUri == session.root && it.relativePath == session.path } }
    val savedCount = currentPhotos.count { it.state == "SAVED" }
    val savingCount = currentPhotos.count { it.state in listOf("CAPTURING", "STAGED", "COPYING") }
    val failedCount = currentPhotos.count { it.state == "LOCAL_FAILED" }
    val latest = currentPhotos.firstOrNull { it.state == "SAVED" }
    val zoomState = handle?.camera?.cameraInfo?.zoomState?.value
    val minZoom = zoomState?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.maxZoomRatio ?: 1f
    val zoom by rememberUpdatedState(model.zoomRatio)
    val shutterLabel = stringResource(R.string.shutter)
    val thumbnailLabel = stringResource(R.string.latest_photo)
    // Reserve the maximum status lines for this sync mode; capture transitions cannot resize the preview.
    val statusLineHeight = with(androidx.compose.ui.platform.LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    val statusHeight = statusLineHeight * (if (model.sync) 4 else 2) + 12.dp
    Surface(Modifier.fillMaxSize(), color = CameraColors.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { model.path = session.path; model.screen = "destination" }) { GlyphIcon(Glyph.BACK, description = stringResource(R.string.change_folder)) }
                Column(Modifier.weight(1f).clickable { fullPath = true }.padding(horizontal = 6.dp)) {
                    Text(stringResource(R.string.current_folder), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(session.path, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    if (handle?.camera?.cameraInfo?.hasFlashUnit() == true) TextButton(onClick = { flashMenu = true }) {
                        GlyphIcon(Glyph.FLASH, Modifier.size(18.dp), tint = if (model.lightMode == LightMode.OFF) CameraColors.onSurfaceVariant else CameraColors.primary)
                        Spacer(Modifier.width(6.dp)); Text(lightLabel(model.lightMode), style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = flashMenu, onDismissRequest = { flashMenu = false }) {
                        LightMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(lightLabel(mode)) }, onClick = { model.lightMode = mode; flashMenu = false }, leadingIcon = { GlyphIcon(Glyph.FLASH, tint = if (mode == model.lightMode) CameraColors.primary else CameraColors.onSurfaceVariant) }) }
                    }
                }
            }
            model.error?.let { ErrorBanner(errorText(it)) { model.error = null } }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                if (permission) CameraPreview(
                    Modifier.fillMaxSize().pointerInput(minZoom, maxZoom) { detectTransformGestures { _, _, scale, _ -> model.zoomRatio = (zoom * scale).coerceIn(minZoom, maxZoom) } },
                    model.lightMode, model.zoomRatio,
                    onReady = { value -> handle = value; value?.camera?.cameraInfo?.zoomState?.value?.let { state -> model.zoomRatio = model.zoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio) } },
                    onFailure = { model.error = "camera_unavailable" },
                ) else Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlyphIcon(Glyph.CAMERA, Modifier.size(40.dp), tint = CameraColors.primary)
                    Text(stringResource(R.string.camera_permission_help), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
                }
                Surface(color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)) {
                    Text(stringResource(R.string.photos_saved_short, savedCount), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (maxZoom > minZoom) Row(Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val zoomLabel = stringResource(R.string.zoom)
                TextButton(onClick = { model.zoomRatio = 1f.coerceIn(minZoom, maxZoom) }, contentPadding = PaddingValues(0.dp)) { Text(String.format(Locale.ROOT, "%.1f×", model.zoomRatio), style = MaterialTheme.typography.labelLarge) }
                Slider(value = model.zoomRatio.coerceIn(minZoom, maxZoom), onValueChange = { model.zoomRatio = it }, valueRange = minZoom..maxZoom, modifier = Modifier.weight(1f).semantics { contentDescription = zoomLabel })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(CameraColors.surfaceVariant).clickable(enabled = latest != null) { model.screen = "browser" }.semantics { contentDescription = thumbnailLabel }, contentAlignment = Alignment.Center) {
                    if (latest != null) PhotoImage(latest.destinationDocumentUri, Modifier.fillMaxSize()) else GlyphIcon(Glyph.PHOTO, tint = CameraColors.onSurfaceVariant)
                }
                Box(Modifier.size(78.dp).border(3.dp, if (handle != null && !model.busy) Color.White else CameraColors.onSurfaceVariant, CircleShape).padding(7.dp).clip(CircleShape).background(if (model.busy) CameraColors.primary else Color.White).clickable(enabled = handle != null && !model.busy) { handle?.let { h -> model.capture { file, done -> takePhoto(context, h.capture, file, done) } } }.semantics { contentDescription = shutterLabel }, contentAlignment = Alignment.Center) {
                    if (model.busy) CircularProgressIndicator(Modifier.size(24.dp), color = CameraColors.onPrimary, strokeWidth = 2.dp)
                }
                IconButton(onClick = { model.screen = "settings" }, modifier = Modifier.size(52.dp)) { GlyphIcon(Glyph.SETTINGS, description = stringResource(R.string.settings)) }
            }
            Column(Modifier.fillMaxWidth().height(statusHeight).padding(horizontal = 22.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (savingCount > 0) Text(stringResource(R.string.saving_short, savingCount), style = MaterialTheme.typography.labelSmall, color = CameraColors.onSurfaceVariant)
                if (failedCount > 0) Text(stringResource(R.string.local_failed_short, failedCount), style = MaterialTheme.typography.labelSmall, color = CameraColors.error)
                if (model.sync) Text(stringResource(R.string.upload_counts, deliveries.count { it.state == "RECEIVED" }, deliveries.count { it.state in listOf("PENDING", "SENDING") }), style = MaterialTheme.typography.labelSmall, color = CameraColors.onSurfaceVariant)
                if (model.sync && deliveries.any { it.state == "FAILED" }) Text(stringResource(R.string.upload_failed), style = MaterialTheme.typography.labelSmall, color = CameraColors.error)
            }
        }
    }
    if (fullPath) AlertDialog(onDismissRequest = { fullPath = false }, title = { Text(stringResource(R.string.current_folder)) }, text = { Text(session.path) }, confirmButton = { TextButton(onClick = { fullPath = false; model.path = session.path; model.screen = "destination" }) { Text(stringResource(R.string.change_folder)) } }, dismissButton = { TextButton(onClick = { fullPath = false }) { Text(stringResource(R.string.close)) } })
}
@Composable fun lightLabel(mode: LightMode): String = stringResource(when (mode) { LightMode.OFF -> R.string.light_off; LightMode.AUTO -> R.string.light_auto; LightMode.FLASH -> R.string.light_flash; LightMode.TORCH -> R.string.light_always })
