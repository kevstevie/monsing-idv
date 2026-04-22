package org.monsing.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "message_received")
class MessageReceived(

    @Id
    @Column(name = "client_message_id", length = 64)
    val clientMessageId: String,

    @Column(name = "message_id", nullable = false, length = 36)
    val messageId: String,

    @Column(name = "sender_id", nullable = false)
    val senderId: Long,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long,

    @Column(name = "received_at", nullable = false)
    val receivedAt: LocalDateTime = LocalDateTime.now()
)
