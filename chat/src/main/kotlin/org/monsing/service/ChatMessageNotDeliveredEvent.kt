package org.monsing.service

data class ChatMessageNotDeliveredEvent(
    val receiverId: Long,
    val chatId: String,
    val senderId: Long,
    val content: String
)
