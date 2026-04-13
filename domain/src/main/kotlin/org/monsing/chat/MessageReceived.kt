package org.monsing.chat

import jakarta.persistence.Id
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "message_received")
class MessageReceived(
    @Id
    val clientMessageId: String,
    val messageId: String,
    val senderId: Long,
    val chatId: String,
    val receivedAt: LocalDateTime = LocalDateTime.now()
)
