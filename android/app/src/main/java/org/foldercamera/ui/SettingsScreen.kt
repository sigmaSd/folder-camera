package org.foldercamera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.foldercamera.R
import org.foldercamera.data.*
import org.foldercamera.sync.*
import org.json.JSONObject

@Composable fun SettingsScreen(model: CameraModel, deliveries: List<Delivery>, captures: List<Capture>, pickRoot: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recoverySource by remember { mutableStateOf<java.io.File?>(null) }
    var recoveryResult by remember { mutableStateOf<Int?>(null) }
    val recoveryExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val source = recoverySource
        if (target != null && source != null) scope.launch {
            recoveryResult = try {
                withContext(Dispatchers.IO) { source.inputStream().use { input -> context.contentResolver.openOutputStream(target, "wt")!!.use { input.copyTo(it) } } }
                R.string.recovery_exported
            } catch (_: Exception) { R.string.export_failed }
        }
    }
    var showEnable by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var includeExisting by remember { mutableStateOf(false) }
    var pairingJson by remember { mutableStateOf("") }
    var address by remember(model.receiver) { mutableStateOf(model.receiver?.endpoint ?: "") }
    var fingerprint by remember { mutableStateOf("") }
    var receiverId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var manualExpanded by remember { mutableStateOf(false) }
    var payloadExpanded by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf(false) }
    var replace by remember { mutableStateOf<Receiver?>(null) }
    var verified by remember { mutableStateOf(false) }
    val lan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) showEnable = true }
    fun hasLan() = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED
    fun pair(text: String) {
        if (!hasLan()) { model.error = "lan_permission"; return }
        operation = true
        scope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { PinnedTransport(context).pair(Pairing.parse(text)) }
                val old = model.receiver
                if (old != null && old.id != paired.id) replace = paired
                else { model.app.config.setReceiver(paired); model.receiver = paired }
            } catch (_: Exception) { model.error = "pair_failed" }
            finally { operation = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        SectionCard(stringResource(R.string.storage_heading), Glyph.FOLDER) {
        Text(model.rootName ?: model.root?.let(::treeLabel) ?: stringResource(R.string.no_root), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.storage_short_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = pickRoot) { Text(stringResource(R.string.choose_base)) }
        if (captures.any { it.state == "LOCAL_FAILED" }) { Text(stringResource(R.string.local_retry_help), style = MaterialTheme.typography.bodySmall); Button(onClick = model::retryLocal) { Text(stringResource(R.string.retry_local)) } }
        recoveryResult?.let { Text(stringResource(it)) }
        captures.filter { it.state == "LOCAL_FAILED" }.forEach { c ->
            Text("${c.relativePath}/${c.filename}"); Text(errorText(c.lastLocalError ?: "storage_unavailable"), color = MaterialTheme.colorScheme.error)
            if (c.byteSize > 0 && c.stagingPath != null) TextButton(onClick = { recoverySource = java.io.File(c.stagingPath); recoveryExport.launch(c.filename) }) { Text(stringResource(R.string.export_recovery)) }
        }
        }
        SectionCard(stringResource(R.string.sync_heading), Glyph.REFRESH) {
        Text(stringResource(R.string.sync_short_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = model.sync, enabled = model.receiver != null, onCheckedChange = { value -> if (!value) model.toggleSync(false) else if (hasLan()) showEnable = true else lan.launch("android.permission.ACCESS_LOCAL_NETWORK") })
            Text(stringResource(R.string.pc_sync))
        }
        if (Build.VERSION.SDK_INT >= 37) TextButton(onClick = { lan.launch("android.permission.ACCESS_LOCAL_NETWORK") }) { Text(stringResource(R.string.allow_lan)) }
        if (model.receiver != null) {
            Text(stringResource(R.string.paired_receiver, model.receiver!!.id))
            OutlinedTextField(address, { address = it; verified = false }, label = { Text(stringResource(R.string.endpoint)) }, modifier = Modifier.fillMaxWidth())
            Button(enabled = !operation, onClick = {
                operation = true; scope.launch {
                    try { val updated = model.receiver!!.copy(endpoint = address); withContext(Dispatchers.IO) { PinnedTransport(context).health(updated) }; model.app.config.setReceiver(updated); model.receiver = updated; verified = true; model.app.delivery.kick() }
                    catch (_: Exception) { model.error = "pair_failed" }; operation = false
                }
            }) { Text(stringResource(R.string.verify_address)) }
            if (verified) Text(stringResource(R.string.connection_verified))
            Text(stringResource(if (model.sync) R.string.upload_pending else R.string.upload_paused))
            Button(onClick = model::retryNetwork, enabled = model.sync) { Text(stringResource(R.string.retry_now)) }
            deliveries.filter { it.state !in listOf("RECEIVED", "DISCARDED") }.forEach { delivery ->
                Text(stringResource(when { !model.sync -> R.string.upload_paused; delivery.state == "SENDING" -> R.string.sending; delivery.state == "FAILED" -> R.string.upload_failed; else -> R.string.upload_pending }))
                delivery.lastError?.let { Text(uploadError(it)) }
            }
        }
        }
        SectionCard(stringResource(R.string.pair_heading), Glyph.CAMERA) {
        Text(stringResource(R.string.pair_short_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { model.screen = "scanner" }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text(stringResource(R.string.scan_qr)) }
        TextButton(onClick = { payloadExpanded = !payloadExpanded }) { Text(stringResource(R.string.advanced_pairing)) }
        if (payloadExpanded) {
        OutlinedTextField(pairingJson, { pairingJson = it }, label = { Text(stringResource(R.string.pair_payload)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !operation, onClick = { pair(pairingJson) }) { Text(stringResource(R.string.pair)) }
        }
        TextButton(onClick = { manualExpanded = !manualExpanded }) { Text(stringResource(R.string.manual_options)) }
        if (manualExpanded) {
        Text(stringResource(R.string.manual_pair_help), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(receiverId, { receiverId = it }, label = { Text(stringResource(R.string.receiver_id)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.endpoint)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fingerprint, { fingerprint = it }, label = { Text(stringResource(R.string.fingerprint)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(secret, { secret = it }, label = { Text(stringResource(R.string.temporary_secret)) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !operation, onClick = { pair(JSONObject().put("version", 1).put("receiverId", receiverId).put("endpoint", address).put("fingerprint", fingerprint).put("secret", secret).put("expiresAt", System.currentTimeMillis() + 300_000).toString()) }) { Text(stringResource(R.string.manual_pair)) }
        }
        }
        AboutSection()
    }
    if (showEnable) AlertDialog(onDismissRequest = { showEnable = false }, title = { Text(stringResource(R.string.enable_sync)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.future_default))
            Row { Checkbox(includeExisting, { includeExisting = it }); Text(stringResource(R.string.include_folders)) }
            if (includeExisting) captures.filter { it.state == "SAVED" }.map { it.relativePath }.distinct().forEach { path ->
                Row { Checkbox(path in selectedPaths, { checked -> selectedPaths = if (checked) selectedPaths + path else selectedPaths - path }); Text(path) }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (includeExisting) model.importFolders(selectedPaths); model.toggleSync(true); showEnable = false }) { Text(stringResource(R.string.enable)) } }, dismissButton = { TextButton(onClick = { showEnable = false }) { Text(stringResource(R.string.cancel)) } })
    replace?.let { r -> AlertDialog(onDismissRequest = { replace = null }, title = { Text(stringResource(R.string.replace_receiver)) }, text = { Text(stringResource(R.string.replace_help)) }, confirmButton = {
        TextButton(onClick = { scope.launch { model.toggleSync(false); model.receiver?.let { model.app.db.dao().discard(it.id) }; model.app.config.setReceiver(r); model.receiver = r; replace = null } }) { Text(stringResource(R.string.discard_replace)) }
    }, dismissButton = { TextButton(onClick = { replace = null }) { Text(stringResource(R.string.keep_receiver)) } }) }
}
@Composable fun QrScreen(model: CameraModel) {
    val context = LocalContext.current
    var cameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraPermission = it }
    val lan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scope = rememberCoroutineScope()
    Text(stringResource(R.string.qr_help))
    if (!cameraPermission) Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
    else if (scanned == null) CameraPreview(Modifier.fillMaxWidth().height(320.dp), onReady = { }, onFailure = { model.error = "camera_unavailable" }, onQr = { scanned = it })
    scanned?.let { payload ->
        val p = runCatching { Pairing.parse(payload) }.getOrNull()
        if (p == null) { Text(stringResource(R.string.pair_invalid)); TextButton(onClick = { scanned = null }) { Text(stringResource(R.string.scan_again)) } }
        else {
            Text(p.endpoint); Text(p.fingerprint)
            if (Build.VERSION.SDK_INT >= 37) Button(onClick = { lan.launch("android.permission.ACCESS_LOCAL_NETWORK") }) { Text(stringResource(R.string.allow_lan)) }
            Text(stringResource(R.string.qr_replace_help))
            Button(enabled = !busy, onClick = {
                busy = true; scope.launch {
                    try {
                        require(model.app.delivery.allowed() || Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED)
                        // Receiver replacement is handled in manual settings, with an explicit queue decision.
                        require(model.receiver == null || model.receiver!!.id == p.receiverId)
                        val r = withContext(Dispatchers.IO) { PinnedTransport(context).pair(p) }
                        model.app.config.setReceiver(r); model.receiver = r; model.screen = "settings"
                    } catch (_: Exception) { model.error = "pair_failed" }; busy = false
                }
            }) { Text(stringResource(R.string.pair)) }
        }
    }
}
@Composable fun uploadError(code: String): String = stringResource(when (code) {
    "unauthenticated" -> R.string.auth_failed; "conflict" -> R.string.upload_conflict; "insufficient_storage" -> R.string.pc_storage_full
    "certificate_changed", "receipt_mismatch" -> R.string.trust_failed; "local_missing", "local_changed", "local_permission" -> R.string.upload_local_missing
    else -> R.string.connection_unavailable
})
