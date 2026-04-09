package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.MessageStatus
import org.monsing.chat.session.LocalSessionStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage

@Component
class MessageRetryScheduler(
    private val messageDeliveryRepository: MessageDeliveryRepository,
    private val messageRepository: MessageRepository,
    private val localSessionStorage: LocalSessionStorage,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 0)
    fun retry() {
        val cutoff = LocalDateTime.now().minusSeconds(ACK_TIMEOUT_SECONDS)
        val pendingDeliveries = messageDeliveryRepository.findPendingOlderThan(cutoff, BATCH_LIMIT)

        if (pendingDeliveries.isEmpty()) {
            Thread.sleep(IDLE_POLL_MS)
            return
        }

        for (delivery in pendingDeliveries) {
            if (delivery.retryCount >= MAX_RETRIES) {
                messageDeliveryRepository.updateStatus(delivery.messageId, delivery.receiverId, MessageStatus.FAILED)
                log.warn("Message delivery failed after {} retries: messageId={}, receiverId={}", MAX_RETRIES, delivery.messageId, delivery.receiverId)
            } else {
                messageDeliveryRepository.incrementRetryCount(delivery.messageId, delivery.receiverId)
                retryDeliver(delivery)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun retryDeliver(delivery: MessageDelivery) {
        val message = messageRepository.findById(delivery.messageId) ?: return
        val sessions = localSessionStorage.getSessionByMemberId(delivery.receiverId) ?: return
        val payload = TextMessage(objectMapper.writeValueAsString(message))
        for (session in sessions) {
            try {
                session.sendMessage(payload)
            } catch (e: Exception) {
                log.warn(
                    "Retry send failed: messageId={}, receiverId={}, attempt={}",
                    delivery.messageId, delivery.receiverId, delivery.retryCount + 1, e
                )
            }
        }
    }

    companion object {
        private const val ACK_TIMEOUT_SECONDS = 30L
        private const val MAX_RETRIES = 3
        private const val BATCH_LIMIT = 100
        private const val IDLE_POLL_MS = 500L
    }
}
