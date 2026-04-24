package org.monsing.service

data class ChatMessageNotDeliveredEvent(
    val receiverId: Long,
    val chatId: Long,
    val senderId: Long,
    val content: String
)
