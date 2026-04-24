package org.monsing.service

data class SendAckFrame(
    val type: String = WebSocketFrameType.SEND_ACK.value,
    val clientMessageId: String,
    val messageId: String,
    val chatId: Long
)
