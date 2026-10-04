package org.foldercamera.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.foldercamera.R
import org.foldercamera.core.FolderPaths
import org.foldercamera.data.Capture

@Composable fun FolderCameraUi(model: CameraModel) {
    val context = LocalContext.current
    val captures by model.captures.collectAsStateWithLifecycle()
    val deliveries by model.deliveries.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            model.selectRoot(uri.toString()); model.retryLocal()
        } catch (_: Exception) { model.error = "storage_permission" }
    }
    MaterialTheme(colorScheme = if (model.screen == "camera") CameraColors else AppColors) {
        if (model.screen == "camera") {
            CaptureScreen(model, captures, deliveries)
        } else {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Accent, shape = RoundedCornerShape(13.dp)) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.CAMERA, tint = androidx.compose.ui.graphics.Color.White) } }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        if (model.gate.session != null) IconButton(onClick = model::back) { GlyphIcon(Glyph.CAMERA, description = stringResource(R.string.return_camera)) }
                    }
                    model.error?.let { ErrorBanner(errorText(it)) { model.error = null } }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (model.screen) {
                            "destination" -> DestinationScreen(model, captures) { picker.launch(model.root?.let(Uri::parse)) }
                            "browser" -> BrowserScreen(model.root, captures)
                            "settings" -> Box(Modifier.padding(horizontal = 20.dp)) { SettingsScreen(model, deliveries, captures) { picker.launch(model.root?.let(Uri::parse)) } }
                            "scanner" -> Column(Modifier.padding(20.dp)) { QrScreen(model) }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        listOf(Triple("destination", R.string.paths, Glyph.FOLDER), Triple("browser", R.string.folders, Glyph.PHOTO), Triple("settings", R.string.settings, Glyph.SETTINGS)).forEach { (screen, label, icon) ->
                            NavigationBarItem(selected = model.screen == screen, onClick = { model.screen = screen }, icon = { GlyphIcon(icon) }, label = { Text(stringResource(label)) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            }
        }
    }
    BackHandler(model.screen != "destination") { model.back() }
}
@Composable fun ErrorBanner(message: String, dismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            IconButton(onClick = dismiss) { GlyphIcon(Glyph.CLOSE, description = stringResource(R.string.dismiss)) }
        }
    }
}
@Composable fun DestinationScreen(model: CameraModel, captures: List<Capture>, selectRoot: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var parent by remember(model.root) { mutableStateOf("") }
    val saved = remember(captures, model.root) { captures.filter { it.baseTreeUri == model.root && it.state == "SAVED" } }
    val known = remember(captures, model.root, model.folderPaths) { FolderPaths.all(model.folderPaths + captures.filter { it.baseTreeUri == model.root && (it.state == "SAVED" || it.creationStarted) }.map { it.relativePath }) }
    val visible = remember(known, parent, search) { FolderPaths.visible(known, parent, search) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val childCounts = remember(known) { known.groupingBy { it.substringBeforeLast('/', "") }.eachCount() }
    val counts = remember(saved) { saved.groupingBy { it.relativePath }.eachCount() }
    LaunchedEffect(model.root, saved.size) { model.refreshFolders() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.choose_folder_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.choose_folder_subtitle), color = Muted, style = MaterialTheme.typography.bodyMedium)
            Surface(onClick = selectRoot, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlyphIcon(Glyph.FOLDER, tint = Accent)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.base_directory), style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(model.rootName ?: model.root?.let(::treeLabel) ?: stringResource(R.string.no_root), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.change), color = Accent, style = MaterialTheme.typography.labelLarge)
                }
            }
            OutlinedTextField(model.path, { model.path = it; model.error = null }, label = { Text(stringResource(R.string.relative_path)) }, placeholder = { Text(stringResource(R.string.path_example)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), isError = model.error?.startsWith("path_") == true, maxLines = 3)
            Button(onClick = model::confirm, enabled = model.root != null, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(54.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                Text(stringResource(R.string.start_camera), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                GlyphIcon(Glyph.ARROW)
            }
            if (model.gate.session != null) TextButton(onClick = model::back, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.cancel_change)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.paths), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp)); Text(known.size.toString(), color = Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (model.folderLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = model::refreshFolders) { GlyphIcon(Glyph.REFRESH, description = stringResource(R.string.refresh_paths), tint = Muted) }
            }
            OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search_paths)) }, leadingIcon = { GlyphIcon(Glyph.SEARCH, tint = Muted) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { GlyphIcon(Glyph.CLOSE, description = stringResource(R.string.clear_search)) } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            if (model.folderError) Text(stringResource(R.string.paths_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (parent.isNotEmpty() && search.isBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { parent = "" }) { GlyphIcon(Glyph.HOME, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.all_paths)) }
                TextButton(onClick = { parent = parent.substringBeforeLast('/', "") }) { GlyphIcon(Glyph.BACK, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(parent.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlyphIcon(Glyph.FOLDER, Modifier.size(32.dp), tint = Muted)
                    Text(stringResource(if (search.isNotEmpty()) R.string.no_matching_paths else R.string.paths_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            items(visible, key = { it }) { path ->
                val childCount = childCounts[path] ?: 0
                Surface(onClick = { model.path = path; model.error = null; focus.clearFocus() }, color = if (model.path == path) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (model.path == path) Accent.copy(alpha = .4f) else Line)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlyphIcon(Glyph.FOLDER, tint = Accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall)
                            if (search.isNotBlank() && path.contains('/')) Text(path.substringBeforeLast('/'), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.path_details, counts[path] ?: 0, childCount), color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (childCount > 0) IconButton(onClick = { parent = path; search = "" }) { GlyphIcon(Glyph.CHEVRON, tint = Muted, description = stringResource(R.string.open_subfolders)) }
                        else Spacer(Modifier.width(14.dp))
                    }
                }
            }
        }
    }
}
fun treeLabel(tree: String): String = runCatching { android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfterLast('/').substringAfter(':') }.getOrDefault(tree)
@Composable fun PhotoImage(uri: String?, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching {
            if (uri == null) return@runCatching null
            val u = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1) }
            context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.photo_preview), modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
}
@Composable fun BrowserScreen(root: String?, captures: List<Capture>) {
    val context = LocalContext.current
    var selectedRoot by remember(root) { mutableStateOf(root) }
    var folder by remember(selectedRoot) { mutableStateOf(selectedRoot?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }) }
    var stack by remember(selectedRoot) { mutableStateOf(listOf<DocumentFile>()) }
    var selected by remember { mutableStateOf<DocumentFile?>(null) }
    val entries by produceState<List<DocumentFile>>(emptyList(), folder) { value = withContext(Dispatchers.IO) { runCatching { folder?.listFiles()?.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name }) ?: emptyList() }.getOrDefault(emptyList()) } }
    val shareTitle = stringResource(R.string.share)
    var exportSource by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val source = exportSource
        if (target != null && source != null) scope.launchWithIo {
            try { context.contentResolver.openInputStream(source)!!.use { input -> context.contentResolver.openOutputStream(target, "wt")!!.use { input.copyTo(it) } } }
            catch (_: Exception) { withContext(Dispatchers.Main) { failed = true } }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.folders), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items((listOfNotNull(root) + captures.map { it.baseTreeUri }).distinct()) { r -> FilterChip(selected = r == selectedRoot, onClick = { selectedRoot = r; selected = null; stack = emptyList() }, label = { Text(treeLabel(r)) }) } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { if (stack.isNotEmpty()) IconButton(onClick = { folder = stack.last(); stack = stack.dropLast(1); selected = null }) { GlyphIcon(Glyph.BACK, description = stringResource(R.string.up_folder)) }; Text(folder?.uri?.let { runCatching { android.provider.DocumentsContract.getDocumentId(it).substringAfter(':') }.getOrDefault(it.toString()) } ?: stringResource(R.string.no_root), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)) } }
        if (failed) item { Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error) }
        selected?.let { photo -> item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column { PhotoImage(photo.uri.toString(), Modifier.fillMaxWidth().height(300.dp)); Row(Modifier.padding(8.dp)) {
                    TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, photo.uri); clipData = android.content.ClipData.newRawUri("photo", photo.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, shareTitle)) }) { Text(stringResource(R.string.share)) }
                    TextButton(onClick = { exportSource = photo.uri; export.launch(photo.name ?: "photo.jpg") }) { Text(stringResource(R.string.export)) }
                } }
            }
        } }
        val unfinishedUris = captures.filter { it.state != "SAVED" }.mapNotNull { it.destinationDocumentUri }.toSet()
        val unfinishedNames = captures.filter { it.state != "SAVED" }.map { it.filename }.toSet()
        items(entries.filter { it.uri.toString() !in unfinishedUris && it.name !in unfinishedNames }, key = { it.uri.toString() }) { file ->
            Surface(onClick = { if (file.isDirectory) { folder?.let { stack = stack + it }; folder = file; selected = null } else if (file.type == "image/jpeg") selected = file }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlyphIcon(if (file.isDirectory) Glyph.FOLDER else Glyph.PHOTO, tint = Accent)
                    Text(file.name ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    GlyphIcon(Glyph.CHEVRON, tint = Muted)
                }
            }
        }
        if (entries.isEmpty()) item { Text(stringResource(R.string.browser_empty), color = Muted) }
    }
}
fun kotlinx.coroutines.CoroutineScope.launchWithIo(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) = this.launch(Dispatchers.IO, block = block)
@Composable fun errorText(code: String): String = stringResource(when (code) {
    "path_empty" -> R.string.path_empty; "path_long" -> R.string.path_long; "path_depth" -> R.string.path_depth
    "path_segment_empty", "path_dot" -> R.string.path_segment_empty; "path_segment_long" -> R.string.path_segment_long
    "path_character" -> R.string.path_character; "path_trailing" -> R.string.path_trailing; "path_reserved" -> R.string.path_reserved
    "storage_permission" -> R.string.storage_permission; "storage_conflict", "storage_renamed" -> R.string.storage_conflict
    "capture_interrupted", "capture_failed", "staging_missing", "staging_corrupt" -> R.string.capture_failed
    "lan_permission" -> R.string.lan_permission; "camera_unavailable" -> R.string.camera_unavailable; "pair_failed" -> R.string.pair_failed; "pair_invalid" -> R.string.pair_invalid
    else -> R.string.storage_unavailable
})
