package org.monsing.api

import java.time.LocalDateTime

data class ChatThumbnailResponse(
    val id: Long,
    val opponentId: Long,
    val senderId: Long?,
    val lastMessage: String?,
    val lastMessageTime: LocalDateTime?
)
