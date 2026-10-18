package org.foldercamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.foldercamera.R
import org.foldercamera.data.Capture
import org.foldercamera.data.Delivery

@Composable fun TransferStatus(model: CameraModel, deliveries: List<Delivery>, captures: List<Capture>) {
    val summary = remember(deliveries, model.receiver?.id) { UploadSummary.forReceiver(deliveries, model.receiver?.id) }
    val outstanding = remember(deliveries, model.receiver?.id) { deliveries.filter { it.receiverId == model.receiver?.id && it.state !in listOf("RECEIVED", "DISCARDED") } }
    val photos = remember(captures) { captures.associateBy { it.photoId } }
    var details by remember { mutableStateOf(false) }
    HorizontalDivider()
    Text(stringResource(when {
        !model.sync -> R.string.upload_paused
        summary.sending > 0 -> R.string.transfers_sending
        summary.failed > 0 -> R.string.transfers_attention
        summary.waiting > 0 -> R.string.transfers_waiting
        else -> R.string.transfers_current
    }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.transfer_totals, summary.received, summary.waiting, summary.sending, summary.failed), style = MaterialTheme.typography.bodySmall)
    outstanding.mapNotNull { it.lastError }.distinct().take(3).forEach { Text(uploadError(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (summary.remaining > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { details = true }) { Text(stringResource(R.string.transfer_details, summary.remaining)) }
            TextButton(onClick = model::retryNetwork, enabled = model.sync && summary.waiting + summary.failed > 0) { Text(stringResource(R.string.retry_now)) }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(stringResource(R.string.transfer_queue)) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(outstanding, key = { it.photoId }) { delivery ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val capture = photos[delivery.photoId]
                    Text(capture?.filename ?: delivery.photoId, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    capture?.let { Text(it.relativePath, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Text(stringResource(when { !model.sync -> R.string.upload_paused; delivery.state == "SENDING" -> R.string.sending; delivery.state == "FAILED" -> R.string.upload_failed; else -> R.string.upload_pending }), style = MaterialTheme.typography.labelMedium)
                    delivery.lastError?.let { Text(uploadError(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.close)) } })
}
