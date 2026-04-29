package org.monsing.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message as FcmMessage
import com.google.firebase.messaging.Notification
import org.monsing.alert.FcmTokenRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.MessageStatus
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!local")
class FcmPushNotificationSender(
    private val firebaseMessaging: FirebaseMessaging,
    private val fcmTokenRepository: FcmTokenRepository,
    private val messageDeliveryRepository: MessageDeliveryRepository,
    private val messageRepository: MessageRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
    fun flushFailed() {
        val failed = messageDeliveryRepository.findAllByStatusOrderByUpdatedAtAsc(
            MessageStatus.FAILED, Pageable.ofSize(BATCH_LIMIT)
        )
        if (failed.isEmpty()) return

        val messagesById = loadMessages(failed)
        val tokensByReceiver = fcmTokenRepository.findTokens(failed.map { it.receiverId })
        val notifiedIds = mutableListOf<Long>()
        val sendable = mutableListOf<Pair<MessageDelivery, List<FcmMessage>>>()

        failed.forEach { delivery ->
            val msgs = buildFcmMessages(delivery, messagesById, tokensByReceiver)
            val id = delivery.id ?: return@forEach
            if (msgs.isEmpty()) notifiedIds.add(id) else sendable.add(delivery to msgs)
        }

        val retried = sendable.mapNotNull { (delivery, msgs) ->
            if (sendAll(msgs)) {
                delivery.id?.also { notifiedIds.add(it) }
                null
            } else {
                delivery
            }
        }
        applyTransitions(notifiedIds, retried)
    }

    private fun sendAll(msgs: List<FcmMessage>): Boolean =
        msgs.chunked(MAX_BATCH_SIZE).all { sendBatch(it) }

    private fun applyTransitions(notifiedIds: List<Long>, retried: List<MessageDelivery>) {
        if (notifiedIds.isNotEmpty()) {
            messageDeliveryRepository.markNotified(notifiedIds)
        }
        if (retried.isEmpty()) return

        val (giveUp, keepRetrying) = retried.partition { it.retryCount + 1 >= MAX_ATTEMPTS }
        val giveUpIds = giveUp.mapNotNull { it.id }
        val keepRetryingIds = keepRetrying.mapNotNull { it.id }
        if (giveUpIds.isNotEmpty()) {
            log.warn("FCM dead-lettered after {} attempts: ids={}", MAX_ATTEMPTS, giveUpIds)
            messageDeliveryRepository.markDeadLettered(giveUpIds)
        }
        if (keepRetryingIds.isNotEmpty()) {
            messageDeliveryRepository.incrementRetry(keepRetryingIds)
        }
    }

    private fun loadMessages(failed: List<MessageDelivery>): Map<String, Message> {
        val ids = failed.map { it.messageId }.distinct()
        return messageRepository.findAllById(ids).associateBy { requireNotNull(it.id) }
    }

    private fun buildFcmMessages(
        delivery: MessageDelivery,
        messagesById: Map<String, Message>,
        tokensByReceiver: Map<Long, Set<String>>
    ): List<FcmMessage> {
        val message = messagesById[delivery.messageId] ?: run {
            log.warn(
                "Message not found for FCM: messageId={}, receiverId={}",
                delivery.messageId, delivery.receiverId
            )
            return emptyList()
        }
        val tokens = tokensByReceiver[delivery.receiverId] ?: emptySet()
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens found for receiverId: {}", delivery.receiverId)
            return emptyList()
        }

        val notification = Notification.builder()
            .setTitle("새 메시지")
            .setBody(message.content)
            .build()

        return tokens.map { token ->
            FcmMessage.builder()
                .setToken(token)
                .setNotification(notification)
                .putData("chatId", message.chatId.toString())
                .putData("senderId", message.senderId.toString())
                .build()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendBatch(batch: List<FcmMessage>): Boolean {
        return try {
            val response = firebaseMessaging.sendEach(batch)
            if (response.failureCount > 0) {
                response.responses.forEachIndexed { index, sendResponse ->
                    if (!sendResponse.isSuccessful) {
                        log.warn(
                            "FCM send failed at batch index {}: {}",
                            index, sendResponse.exception?.message
                        )
                    }
                }
            }
            log.debug(
                "FCM batch sent: size={}, success={}, failure={}",
                batch.size, response.successCount, response.failureCount
            )
            true
        } catch (e: Exception) {
            log.error("Failed to send FCM batch (size={})", batch.size, e)
            false
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 500
        private const val BATCH_LIMIT = 500
        private const val SCAN_INTERVAL_MS = 1000L
        private const val MAX_ATTEMPTS = 3
    }
}
