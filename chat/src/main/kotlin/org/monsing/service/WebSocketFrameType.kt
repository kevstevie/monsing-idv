package org.monsing.service

enum class WebSocketFrameType(val value: String) {
    CHAT("CHAT"),
    ACK("ACK"),
    SEND_ACK("SEND_ACK");

    companion object {
        fun fromValue(value: String): WebSocketFrameType? = entries.find { it.value == value }
    }
}
