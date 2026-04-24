package org.monsing.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "message",
    indexes = [
        Index(name = "idx_message_chat_id_id", columnList = "chat_id, id")
    ]
)
class Message(

    @Id
    @Column(length = 36)
    var id: String? = null,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long,

    @Column(name = "sender_id", nullable = false)
    val senderId: Long,

    @Column(nullable = false, length = 1000)
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
