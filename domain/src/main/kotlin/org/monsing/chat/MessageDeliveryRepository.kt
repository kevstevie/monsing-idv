package org.monsing.chat

import java.time.LocalDateTime
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface MessageDeliveryRepository : JpaRepository<MessageDelivery, Long> {

    @Modifying
    @Transactional
    @Query(
        """
        update MessageDelivery md
           set md.status = :status,
               md.updatedAt = CURRENT_TIMESTAMP
         where md.messageId = :messageId
           and md.receiverId = :receiverId
        """
    )
    fun updateStatus(
        @Param("messageId") messageId: String,
        @Param("receiverId") receiverId: Long,
        @Param("status") status: MessageStatus
    ): Int

    fun findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(
        statuses: Collection<MessageStatus>,
        updatedAt: LocalDateTime,
        pageable: Pageable
    ): List<MessageDelivery>

    fun findAllByStatusOrderByUpdatedAtAsc(
        status: MessageStatus,
        pageable: Pageable
    ): List<MessageDelivery>

    @Modifying
    @Transactional
    @Query(
        """
        update MessageDelivery md
           set md.status = org.monsing.chat.MessageStatus.FAILED,
               md.updatedAt = CURRENT_TIMESTAMP
         where md.id in :ids
        """
    )
    fun markFailed(@Param("ids") ids: Collection<Long>): Int

    @Modifying
    @Transactional
    @Query(
        """
        update MessageDelivery md
           set md.status = org.monsing.chat.MessageStatus.NOTIFIED,
               md.updatedAt = CURRENT_TIMESTAMP
         where md.id in :ids
        """
    )
    fun markNotified(@Param("ids") ids: Collection<Long>): Int
}
