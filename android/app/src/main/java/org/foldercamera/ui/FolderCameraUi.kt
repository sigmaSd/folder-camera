package org.foldercamera.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var search by androidx.compose.runtime.saveable.rememberSaveable(model.root) { mutableStateOf("") }
    var parent by androidx.compose.runtime.saveable.rememberSaveable(model.root) { mutableStateOf("") }
    val saved = remember(captures, model.root) { captures.filter { it.baseTreeUri == model.root && it.state == "SAVED" } }
    val known = remember(captures, model.root, model.folderPaths) { FolderPaths.all(model.folderPaths + captures.filter { it.baseTreeUri == model.root && (it.state == "SAVED" || it.creationStarted) }.map { it.relativePath }) }
    val visible = remember(known, parent, search) { FolderPaths.visible(known, parent, search) }
    val childCounts = remember(known) { known.groupingBy { it.substringBeforeLast('/', "") }.eachCount() }
    val counts = remember(saved) { saved.groupingBy { it.relativePath }.eachCount() }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(model.root, saved.size) { model.refreshFolders() }
    LaunchedEffect(parent, search) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.choose_folder_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Surface(onClick = selectRoot, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlyphIcon(Glyph.FOLDER, tint = Accent)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.base_directory), style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(model.rootName ?: model.root?.let(::treeLabel) ?: stringResource(R.string.no_root), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(R.string.change), color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchField(search, { search = it }, stringResource(R.string.search_paths), Modifier.weight(1f))
            if (model.folderLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            IconButton(onClick = model::refreshFolders) { GlyphIcon(Glyph.REFRESH, description = stringResource(R.string.refresh_paths), tint = Muted) }
        }
        if (parent.isNotEmpty() && search.isBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { parent = parent.substringBeforeLast('/', "") }) { GlyphIcon(Glyph.BACK, description = stringResource(R.string.up_folder)) }
                Text(parent, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { parent = "" }) { Text(stringResource(R.string.all_paths)) }
            }
        }
        if (model.folderError) Text(stringResource(R.string.paths_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("path-list"), state = listState, contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlyphIcon(Glyph.FOLDER, Modifier.size(32.dp), tint = Muted)
                    Text(stringResource(if (search.isNotBlank()) R.string.no_matching_paths else R.string.paths_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            items(visible, key = { it }) { path ->
                val childCount = childCounts[path] ?: 0
                Surface(onClick = { model.path = path; model.error = null; focus.clearFocus() }, color = if (model.path == path) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (model.path == path) Accent.copy(alpha = .4f) else Line)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlyphIcon(Glyph.FOLDER, tint = Accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (search.isNotBlank() && path.contains('/')) Text(path.substringBeforeLast('/'), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.path_details, counts[path] ?: 0, childCount), color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (childCount > 0) IconButton(onClick = { parent = path; search = ""; focus.clearFocus() }) { GlyphIcon(Glyph.CHEVRON, tint = Muted, description = stringResource(R.string.open_subfolders)) }
                        else Spacer(Modifier.width(14.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(model.path, { model.path = it; model.error = null }, label = { Text(stringResource(R.string.relative_path)) }, placeholder = { Text(stringResource(R.string.path_example)) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), isError = model.error?.startsWith("path_") == true)
            Button(onClick = { focus.clearFocus(); model.confirm() }, enabled = model.root != null, shape = RoundedCornerShape(14.dp), modifier = Modifier.width(110.dp).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(R.string.start_camera), maxLines = 2)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
@Composable fun SearchField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, singleLine = true, label = { Text(label) }, leadingIcon = { GlyphIcon(Glyph.SEARCH, tint = Muted) }, trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { GlyphIcon(Glyph.CLOSE, description = stringResource(R.string.clear_search)) } }, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth())
}
fun treeLabel(tree: String): String = runCatching { android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfterLast('/').substringAfter(':') }.getOrDefault(tree)
@Composable fun errorText(code: String): String = stringResource(when (code) {
    "path_empty" -> R.string.path_empty; "path_long" -> R.string.path_long; "path_depth" -> R.string.path_depth
    "path_segment_empty", "path_dot" -> R.string.path_segment_empty; "path_segment_long" -> R.string.path_segment_long
    "path_character" -> R.string.path_character; "path_trailing" -> R.string.path_trailing; "path_reserved" -> R.string.path_reserved
    "storage_permission" -> R.string.storage_permission; "storage_conflict", "storage_renamed" -> R.string.storage_conflict
    "capture_interrupted", "capture_failed", "staging_missing", "staging_corrupt" -> R.string.capture_failed
    "lan_permission" -> R.string.lan_permission; "camera_unavailable" -> R.string.camera_unavailable; "pair_failed" -> R.string.pair_failed; "pair_invalid" -> R.string.pair_invalid
    "pair_expired" -> R.string.pair_expired; "pair_clock" -> R.string.pair_clock; "pair_unreachable" -> R.string.pair_unreachable
    "pair_wrong_pc" -> R.string.pair_wrong_pc; "pair_used" -> R.string.pair_used; "pair_rate_limited" -> R.string.pair_rate_limited; "address_failed" -> R.string.address_failed
    else -> R.string.storage_unavailable
})
