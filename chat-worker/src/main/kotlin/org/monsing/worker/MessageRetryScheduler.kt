package org.monsing.worker

import java.time.LocalDateTime
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MessageRetryScheduler(
    private val messageDeliveryRepository: MessageDeliveryRepository
) {

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
    @Transactional
    fun expireUndelivered() {
        val cutoff = LocalDateTime.now().minusSeconds(ACK_TIMEOUT_SECONDS)
        val expired = messageDeliveryRepository.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(
            UNDELIVERED_STATUSES, cutoff, Pageable.ofSize(BATCH_LIMIT)
        )
        expired.forEach { it.transitionTo(MessageStatus.FAILED) }
    }

    companion object {
        private const val ACK_TIMEOUT_SECONDS = 30L
        private const val BATCH_LIMIT = 100
        private const val SCAN_INTERVAL_MS = 1000L
        private val UNDELIVERED_STATUSES = listOf(MessageStatus.PENDING, MessageStatus.RELAY_PENDING)
    }
}
