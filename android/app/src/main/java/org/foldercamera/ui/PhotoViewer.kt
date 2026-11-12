package org.foldercamera.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

data class ViewerPhoto(val uri: String, val name: String)

@Composable fun PhotoViewer(photos: List<ViewerPhoto>, start: Int, onClose: () -> Unit, onShare: (ViewerPhoto) -> Unit, onExport: (ViewerPhoto) -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    val current = photos.getOrNull(pager.currentPage) ?: return
    var openFailed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { openFailed = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        MaterialTheme(colorScheme = CameraColors) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { GlyphIcon(Glyph.CLOSE, description = stringResource(R.string.close_photo)) }
                        Text(current.name, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (photos.size > 1) Text(stringResource(R.string.photo_position, pager.currentPage + 1, photos.size), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("photo-pager"), key = { photos[it].uri }, beyondViewportPageCount = 1) { page ->
                        PhotoImage(photos[page].uri, Modifier.fillMaxSize(), ContentScale.Fit, showStatus = true)
                    }
                    if (openFailed) Text(stringResource(R.string.open_failed), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onShare(current) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.share), maxLines = 1) }
                        OutlinedButton(onClick = { onExport(current) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export), maxLines = 1) }
                        Button(onClick = {
                            // Hand the photo to the user's gallery/viewer; Android shows its app picker unless a default is set.
                            val uri = Uri.parse(current.uri)
                            openFailed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/jpeg"); clipData = ClipData.newRawUri(current.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }) }.isFailure
                        }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.open_with), maxLines = 1) }
                    }
                }
            }
        }
    }
}
