package org.monsing.chat

import java.time.LocalDateTime
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MessageDeliveryRepository : JpaRepository<MessageDelivery, Long> {

    fun findByMessageIdAndReceiverId(messageId: String, receiverId: Long): MessageDelivery?

    fun findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(
        statuses: Collection<MessageStatus>,
        updatedAt: LocalDateTime,
        pageable: Pageable
    ): List<MessageDelivery>

    fun findAllByStatusOrderByUpdatedAtAsc(
        status: MessageStatus,
        pageable: Pageable
    ): List<MessageDelivery>
}
