package org.monsing.chat

import java.time.LocalDateTime
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

interface JpaMessageDeliveryRepository : JpaRepository<MessageDelivery, Long> {

    @Modifying
    @Query(
        """
        update MessageDelivery md
           set md.status = :status,
               md.updatedAt = :updatedAt
         where md.messageId = :messageId
           and md.receiverId = :receiverId
        """
    )
    fun updateStatus(
        @Param("messageId") messageId: String,
        @Param("receiverId") receiverId: Long,
        @Param("status") status: MessageStatus,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Modifying
    @Query(
        """
        update MessageDelivery md
           set md.retryCount = md.retryCount + 1,
               md.updatedAt = :updatedAt
         where md.messageId = :messageId
           and md.receiverId = :receiverId
        """
    )
    fun incrementRetryCount(
        @Param("messageId") messageId: String,
        @Param("receiverId") receiverId: Long,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    fun findAllByStatusAndUpdatedAtLessThan(
        status: MessageStatus,
        updatedAt: LocalDateTime,
        pageable: Pageable
    ): List<MessageDelivery>
}

@Component
class MessageDeliveryRepository(
    private val jpaMessageDeliveryRepository: JpaMessageDeliveryRepository
) {

    fun save(delivery: MessageDelivery): MessageDelivery {
        return jpaMessageDeliveryRepository.save(delivery)
    }

    fun saveAll(deliveries: List<MessageDelivery>) {
        if (deliveries.isNotEmpty()) jpaMessageDeliveryRepository.saveAll(deliveries)
    }

    @Transactional
    fun updateStatus(messageId: String, receiverId: Long, status: MessageStatus) {
        jpaMessageDeliveryRepository.updateStatus(messageId, receiverId, status, LocalDateTime.now())
    }

    @Transactional
    fun incrementRetryCount(messageId: String, receiverId: Long) {
        jpaMessageDeliveryRepository.incrementRetryCount(messageId, receiverId, LocalDateTime.now())
    }

    fun findPendingOlderThan(cutoffTime: LocalDateTime, limit: Int): List<MessageDelivery> {
        return jpaMessageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(
            MessageStatus.PENDING,
            cutoffTime,
            Pageable.ofSize(limit)
        )
    }
}
