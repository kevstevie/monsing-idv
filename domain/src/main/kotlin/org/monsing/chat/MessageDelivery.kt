package org.monsing.chat

import jakarta.persistence.Id
import java.time.LocalDateTime
import org.springframework.data.mongodb.core.mapping.Document

@Document
class MessageDelivery(

    @Id
    var id: String? = null,
    val messageId: String,
    val receiverId: Long,
    var status: MessageStatus = MessageStatus.PENDING,
    var retryCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
