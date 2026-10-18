package org.foldercamera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.foldercamera.R

@Composable fun PhotoImage(uri: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, showStatus: Boolean = false) {
    val context = LocalContext.current
    val result by produceState<Result<Bitmap>?>(null, uri) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching {
            val source = Uri.parse(requireNotNull(uri))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            requireNotNull(context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) })
        } }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        result?.getOrNull()?.let { Image(it.asImageBitmap(), stringResource(R.string.photo_preview), Modifier.fillMaxSize(), contentScale = contentScale) }
        if (showStatus && result == null) CircularProgressIndicator()
        if (showStatus && result?.isFailure == true) Text(stringResource(R.string.photo_unavailable), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable fun PhotoViewer(uri: String, name: String, onClose: () -> Unit, onShare: () -> Unit, onExport: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MaterialTheme(colorScheme = CameraColors) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { GlyphIcon(Glyph.CLOSE, description = stringResource(R.string.close_photo)) }
                        Text(name, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    PhotoImage(uri, Modifier.weight(1f).fillMaxWidth(), ContentScale.Fit, showStatus = true)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.share)) }
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export)) }
                    }
                }
            }
        }
    }
}
