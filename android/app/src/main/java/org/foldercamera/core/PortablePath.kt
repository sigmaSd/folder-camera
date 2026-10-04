package org.foldercamera.core

/** Exact Unicode is preserved. Limits count UTF-8 bytes, matching the v1 receiver. */
object PortablePath {
    private val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(?:\\..*)?$")
    fun validate(path: String): String? {
        if (path.isEmpty()) return "path_empty"
        if (path.toByteArray(Charsets.UTF_8).size > 1024) return "path_long"
        val segments = path.split('/')
        if (segments.any { it.isEmpty() }) return "path_segment_empty"
        if (segments.size > 32) return "path_depth"
        return segments.firstNotNullOfOrNull { validateSegment(it) }
    }
    fun validateSegment(segment: String): String? = when {
        segment.isEmpty() || segment == "." || segment == ".." -> "path_dot"
        segment.indices.any { i -> (segment[i].isHighSurrogate() && (i + 1 >= segment.length || !segment[i+1].isLowSurrogate())) || (segment[i].isLowSurrogate() && (i == 0 || !segment[i-1].isHighSurrogate())) } -> "path_character"
        segment.toByteArray(Charsets.UTF_8).size > 120 -> "path_segment_long"
        segment.any { it.code < 32 || it.code in 127..159 || it in "\\/:*?\"<>|%" } -> "path_character"
        segment.endsWith('.') || segment.endsWith(' ') -> "path_trailing"
        segment == ".folder-camera-partials" -> "path_reserved"
        reserved.matches(segment) -> "path_reserved"
        else -> null
    }
}

data class CaptureSession(val root: String, val path: String)
class DestinationGate {
    var session: CaptureSession? = null; private set
    fun confirm(root: String, path: String) {
        require(root.isNotEmpty() && PortablePath.validate(path) == null)
        session = CaptureSession(root, path)
    }
    // Editing does not mutate the destination until a valid confirmation.
    fun reset() { session = null }
    fun cancel(): CaptureSession? = session
}
object DeliveryPolicy {
    fun claimable(state: String, leaseUntil: Long, nextRetry: Long, now: Long): Boolean =
        (state == "PENDING" || state == "SENDING" && leaseUntil <= now) && nextRetry <= now
    fun retryDelay(attempts: Int): Long = (15_000L * (1L shl attempts.coerceIn(0, 10))).coerceAtMost(6 * 60 * 60 * 1000L)
}
