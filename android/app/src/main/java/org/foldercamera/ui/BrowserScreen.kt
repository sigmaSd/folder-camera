package org.foldercamera.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.foldercamera.R
import org.foldercamera.data.Capture

private data class BrowserFile(val document: DocumentFile, val name: String, val directory: Boolean, val jpeg: Boolean) {
    val uri get() = document.uri.toString()
}

@Composable fun BrowserScreen(root: String?, captures: List<Capture>) {
    val context = LocalContext.current
    var selectedRoot by rememberSaveable(root) { mutableStateOf(root) }
    var folder by remember(selectedRoot) { mutableStateOf(selectedRoot?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }) }
    var stack by remember(selectedRoot) { mutableStateOf(listOf<DocumentFile>()) }
    var search by rememberSaveable(selectedRoot) { mutableStateOf("") }
    var selected by remember { mutableStateOf<BrowserFile?>(null) }
    var entries by remember(folder) { mutableStateOf<List<BrowserFile>?>(null) }
    var readFailed by remember(folder) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var failed by remember { mutableStateOf(false) }
    var exportSource by remember { mutableStateOf<BrowserFile?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val source = exportSource
        if (target != null && source != null) scope.launch {
            failed = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(Uri.parse(source.uri))!!.use { input -> context.contentResolver.openOutputStream(target, "wt")!!.use { input.copyTo(it) } } }
                false
            } catch (_: Exception) { true }
        }
    }
    LaunchedEffect(folder, refresh, captures) {
        readFailed = false
        val result = withContext(Dispatchers.IO) { runCatching {
            val current = folder ?: return@runCatching emptyList()
            check(current.exists() && current.canRead())
            current.listFiles().map { BrowserFile(it, it.name.orEmpty(), it.isDirectory, it.type == "image/jpeg") }
                .sortedWith(compareBy<BrowserFile> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        } }
        entries = result.getOrDefault(emptyList()); readFailed = result.isFailure
    }
    LaunchedEffect(folder, search) { listState.scrollToItem(0) }
    val unfinished = remember(captures, selectedRoot) { captures.filter { it.baseTreeUri == selectedRoot && it.state != "SAVED" } }
    val unfinishedUris = remember(unfinished) { unfinished.mapNotNull { it.destinationDocumentUri }.toSet() }
    val unfinishedNames = remember(unfinished) { unfinished.map { it.filename }.toSet() }
    val visible = remember(entries, search, unfinishedUris, unfinishedNames) { entries.orEmpty().filter { it.uri !in unfinishedUris && it.name !in unfinishedNames && (search.isBlank() || it.name.contains(search, ignoreCase = true)) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.folders), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        val roots = remember(root, captures) { (listOfNotNull(root) + captures.map { it.baseTreeUri }).distinct() }
        if (roots.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roots, key = { it }) { r -> FilterChip(selected = r == selectedRoot, onClick = { selectedRoot = r; selected = null; stack = emptyList() }, label = { Text(treeLabel(r)) }) }
        }
        SearchField(search, { search = it }, stringResource(R.string.search_files))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stack.isNotEmpty()) IconButton(onClick = { folder = stack.last(); stack = stack.dropLast(1); search = "" }) { GlyphIcon(Glyph.BACK, description = stringResource(R.string.up_folder)) }
            Text(folder?.uri?.let { runCatching { android.provider.DocumentsContract.getDocumentId(it).substringAfter(':') }.getOrDefault(it.toString()) } ?: stringResource(R.string.no_root), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { refresh++ }) { GlyphIcon(Glyph.REFRESH, description = stringResource(R.string.refresh_files)) }
        }
        if (failed) Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("browser-list"), state = listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entries == null) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
            else if (readFailed) item { Text(stringResource(R.string.browser_unavailable), color = MaterialTheme.colorScheme.error) }
            else if (visible.isEmpty()) item { Text(stringResource(if (search.isNotBlank()) R.string.no_matching_files else R.string.browser_empty), color = Muted) }
            items(visible, key = { it.uri }) { file ->
                Surface(onClick = {
                    if (file.directory) { folder?.let { stack = stack + it }; folder = file.document; search = "" }
                    else if (file.jpeg) selected = file
                }, enabled = file.directory || file.jpeg, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlyphIcon(if (file.directory) Glyph.FOLDER else Glyph.PHOTO, tint = Accent)
                        Text(file.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (file.directory || file.jpeg) GlyphIcon(Glyph.CHEVRON, tint = Muted)
                    }
                }
            }
        }
    }
    val shareTitle = stringResource(R.string.share)
    selected?.let { photo ->
        PhotoViewer(photo.uri, photo.name, onClose = { selected = null }, onShare = {
            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"; val uri = Uri.parse(photo.uri); putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("photo", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, shareTitle)) }.onFailure { failed = true }
        }, onExport = { exportSource = photo; export.launch(photo.name) })
    }
}
