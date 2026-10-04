package org.foldercamera

import org.junit.Assert.*
import org.junit.Test
import org.foldercamera.core.*

class CoreTest {
    @Test fun unicodeAndNestedPathsArePreserved() {
        listOf("Projects/Job A/Before", "Été/صور/قبل", " A/B", "Projet/📷").forEach { assertNull(it, PortablePath.validate(it)) }
    }
    @Test fun portableNamesAndTraversalAreRejected() {
        listOf("", "/a", "a/", "a//b", ".", "..", "a/../b", "C:/a", "a\\b", "a\u0000", "CON", "con.jpg", "COM1", "LPT².txt", "a.", "a ", "a%2fb", "a?b", "bad\uD800").forEach { assertNotNull(it, PortablePath.validate(it)) }
        assertNotNull(PortablePath.validate("é".repeat(61)))
        assertNotNull(PortablePath.validate((1..33).joinToString("/") { "a" }))
    }
    @Test fun gateRequiresConfirmationAndCancelKeepsDestination() {
        val gate = DestinationGate(); assertNull(gate.session)
        gate.confirm("content://root", "One/Two")
        assertEquals(CaptureSession("content://root", "One/Two"), gate.cancel())
        assertThrows(IllegalArgumentException::class.java) { gate.confirm("content://root", "../bad") }
        assertEquals("One/Two", gate.session!!.path)
        gate.confirm("content://root", "New/صور"); assertEquals("New/صور", gate.session!!.path)
        assertNull(DestinationGate().session)
    }
    @Test fun durableLeaseAndBackoffPolicy() {
        assertFalse(DeliveryPolicy.claimable("SENDING", 200, 0, 100))
        assertTrue(DeliveryPolicy.claimable("SENDING", 200, 0, 200))
        assertFalse(DeliveryPolicy.claimable("RECEIVED", 0, 0, 500))
        assertFalse(DeliveryPolicy.claimable("FAILED", 0, 0, 500))
        assertFalse(DeliveryPolicy.claimable("PENDING", 0, 600, 500))
        assertTrue(DeliveryPolicy.retryDelay(2) > DeliveryPolicy.retryDelay(1))
        assertTrue(DeliveryPolicy.retryDelay(100) <= 6 * 60 * 60 * 1000L)
    }
}
