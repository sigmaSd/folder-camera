package org.foldercamera.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.foldercamera.BuildConfig
import org.foldercamera.R

@Composable fun AboutSection() {
    val context = LocalContext.current
    var legal by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    LaunchedEffect(legal) { legal?.let { file -> body = withContext(Dispatchers.IO) { context.assets.open("legal/$file").bufferedReader().use { it.readText() } } } }
    val privacyTitle = stringResource(R.string.privacy_policy)
    val licenseTitle = stringResource(R.string.open_source_license)
    SectionCard(stringResource(R.string.about), Glyph.CAMERA) {
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { title = privacyTitle; body = ""; legal = "privacy.txt" }) { Text(privacyTitle) }
        TextButton(onClick = { title = licenseTitle; body = ""; legal = "GPL-3.0.txt" }) { Text(licenseTitle) }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sigmasd/folder-camera"))) }) { Text(stringResource(R.string.source_code)) }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sigmasd/folder-camera/issues"))) }) { Text(stringResource(R.string.help_feedback)) }
    }
    if (legal != null) AlertDialog(onDismissRequest = { legal = null }, title = { Text(title) }, text = { Text(body, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton(onClick = { legal = null }) { Text(stringResource(R.string.close)) } })
}
