package org.foldercamera.ui

import org.foldercamera.data.Delivery

data class UploadSummary(val waiting: Int, val sending: Int, val failed: Int, val received: Int) {
    val remaining get() = waiting + sending + failed
    companion object {
        fun forReceiver(deliveries: List<Delivery>, receiverId: String?): UploadSummary {
            val current = deliveries.filter { receiverId != null && it.receiverId == receiverId }
            return UploadSummary(current.count { it.state == "PENDING" }, current.count { it.state == "SENDING" },
                current.count { it.state == "FAILED" }, current.count { it.state == "RECEIVED" })
        }
    }
}
