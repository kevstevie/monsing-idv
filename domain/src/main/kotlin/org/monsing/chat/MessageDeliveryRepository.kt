package org.monsing.chat

import java.time.LocalDateTime
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.stereotype.Component

@Component
class MessageDeliveryRepository(
    private val mongoTemplate: MongoTemplate
) {

    fun save(delivery: MessageDelivery): MessageDelivery {
        return mongoTemplate.insert(delivery)
    }

    fun updateStatus(messageId: String, receiverId: Long, status: MessageStatus) {
        val query = Query().addCriteria(byMessageAndReceiver(messageId, receiverId))
        val update = Update()
            .set(MessageDelivery::status.name, status)
            .set(MessageDelivery::updatedAt.name, LocalDateTime.now())
        mongoTemplate.updateFirst(query, update, MessageDelivery::class.java)
    }

    fun incrementRetryCount(messageId: String, receiverId: Long) {
        val query = Query().addCriteria(byMessageAndReceiver(messageId, receiverId))
        val update = Update()
            .inc(MessageDelivery::retryCount.name, 1)
            .set(MessageDelivery::updatedAt.name, LocalDateTime.now())
        mongoTemplate.updateFirst(query, update, MessageDelivery::class.java)
    }

    fun findPendingOlderThan(cutoffTime: LocalDateTime, limit: Int): List<MessageDelivery> {
        val query = Query().addCriteria(
            Criteria().andOperator(
                MessageDelivery::status isEqualTo MessageStatus.PENDING,
                MessageDelivery::updatedAt lt cutoffTime
            )
        ).limit(limit)
        return mongoTemplate.find(query, MessageDelivery::class.java)
    }

    private fun byMessageAndReceiver(messageId: String, receiverId: Long): Criteria =
        Criteria().andOperator(
            MessageDelivery::messageId isEqualTo messageId,
            MessageDelivery::receiverId isEqualTo receiverId
        )
}
