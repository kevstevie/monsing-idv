package org.monsing.service

data class ChatMessageSentEvent(
    val receiverId: Long,
    val chatId: String,
    val senderId: Long,
    val content: String
)