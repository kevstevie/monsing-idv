package org.monsing.service

class AckDeliveryFailedException(
    val senderId: Long,
    val chatId: Long,
    val messageId: String,
    val clientMessageId: String?
) : RuntimeException(
    "Failed to deliver ACK to sender: senderId=$senderId, chatId=$chatId, " +
        "messageId=$messageId, clientMessageId=$clientMessageId"
)
