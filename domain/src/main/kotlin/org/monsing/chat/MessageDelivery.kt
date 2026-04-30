package org.monsing.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "message_delivery",
    indexes = [
        Index(name = "idx_message_delivery_status_updated_at", columnList = "status, updated_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_message_delivery_message_receiver",
            columnNames = ["message_id", "receiver_id"]
        )
    ]
)
class MessageDelivery(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "message_id", nullable = false, length = 36)
    val messageId: String,

    @Column(name = "receiver_id", nullable = false)
    val receiverId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MessageStatus = MessageStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    @Version
    @Column(nullable = false)
    var version: Long = 0L
        protected set

    fun transitionTo(target: MessageStatus) {
        if (!canTransitionTo(target)) return
        status = target
        updatedAt = LocalDateTime.now()
    }

    fun incrementRetry() {
        check(status == MessageStatus.FAILED) { "incrementRetry only on FAILED, was $status" }
        retryCount += 1
        updatedAt = LocalDateTime.now()
    }

    private fun canTransitionTo(target: MessageStatus): Boolean = status.canTransitionTo(target)
}
