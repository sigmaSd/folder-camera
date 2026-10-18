package org.foldercamera.sync

import org.foldercamera.data.CameraDao
import org.foldercamera.data.Receiver

/** Address/credential renewal and QR pairing share the same queue ownership rules. */
class ReceiverConnections(
    private val dao: CameraDao,
    private val current: () -> Receiver?,
    private val persist: (Receiver) -> Unit,
) {
    suspend fun connect(next: Receiver, replace: Boolean = false): Boolean {
        val previous = current()
        val differentPc = previous != null && previous.id != next.id
        if (differentPc && !replace) return false
        if (differentPc) dao.discard(previous!!.id)
        persist(next)
        // A verified new address or fresh credential must also release old failure/backoff state.
        dao.retry(next.id)
        return true
    }
}
