package org.foldercamera

import org.foldercamera.data.Delivery
import org.foldercamera.ui.UploadSummary
import org.junit.Assert.*
import org.junit.Test

class UploadSummaryTest {
    @Test fun summaryGroupsTransfersForOnlyTheSelectedPc() {
        val queue = listOf(
            Delivery("one", "current"), Delivery("two", "current"),
            Delivery("three", "current", state = "SENDING"),
            Delivery("four", "current", state = "FAILED"),
            Delivery("five", "current", state = "RECEIVED"),
            Delivery("six", "current", state = "DISCARDED"),
            Delivery("seven", "previous", state = "FAILED"), Delivery("eight", "previous"),
        )
        val summary = UploadSummary.forReceiver(queue, "current")
        assertEquals(2, summary.waiting); assertEquals(1, summary.sending)
        assertEquals(1, summary.failed); assertEquals(1, summary.received)
        assertEquals(4, summary.remaining)
        assertEquals(UploadSummary(0, 0, 0, 0), UploadSummary.forReceiver(queue, null))
        assertEquals(UploadSummary(0, 0, 0, 0), UploadSummary.forReceiver(queue, "new-pc"))
    }
}
