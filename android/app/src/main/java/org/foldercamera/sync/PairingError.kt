package org.foldercamera.sync

import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/** Maps a pairing failure to the error code naming the step that failed, so the message points at the real cause. */
fun pairingError(error: Throwable): String {
    if (error is ProtocolFailure) return when (error.code) {
        "pairing_payload" -> "pair_invalid"; "pairing_expired" -> "pair_expired"; "pairing_clock" -> "pair_clock"
        "pairing_invalid" -> "pair_used"; "rate_limited", "busy" -> "pair_rate_limited"
        "certificate_changed", "receiver_mismatch" -> "pair_wrong_pc"; "lan_permission" -> "lan_permission"
        else -> "pair_failed"
    }
    val chain = generateSequence(error) { it.cause }.take(16).toList()
    return when {
        chain.any { it is CertificateException || it is SSLPeerUnverifiedException } -> "pair_wrong_pc"
        chain.any { it is SSLException } -> "pair_failed"
        error is IOException -> "pair_unreachable"
        else -> "pair_failed"
    }
}
