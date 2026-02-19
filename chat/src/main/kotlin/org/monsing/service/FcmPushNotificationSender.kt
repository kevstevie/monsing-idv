package org.monsing.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import java.util.concurrent.ConcurrentLinkedQueue
import org.monsing.alert.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!local")
class FcmPushNotificationSender(
    private val firebaseMessaging: FirebaseMessaging,
    private val fcmTokenRepository: FcmTokenRepository
) : PushNotificationSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ConcurrentLinkedQueue<ChatMessageNotDeliveredEvent>()

    @EventListener
    override fun handle(event: ChatMessageNotDeliveredEvent) {
        buffer.add(event)
    }

    @Scheduled(fixedDelay = 200)
    fun flush() {
        val events = drainBuffer()
        if (events.isEmpty()) return

        val messages = events.flatMap { event -> buildMessages(event) }
        if (messages.isEmpty()) return

        messages.chunked(MAX_BATCH_SIZE).forEach { batch ->
            sendBatch(batch)
        }
    }

    private fun drainBuffer(): List<ChatMessageNotDeliveredEvent> {
        return generateSequence { buffer.poll() }.toList()
    }

    private fun buildMessages(event: ChatMessageNotDeliveredEvent): List<Message> {
        val tokens = fcmTokenRepository.findToken(event.receiverId)
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens found for receiverId: {}", event.receiverId)
            return emptyList()
        }

        val notification = Notification.builder()
            .setTitle("새 메시지")
            .setBody(event.content)
            .build()

        return tokens.map { token ->
            Message.builder()
                .setToken(token)
                .setNotification(notification)
                .putData("chatId", event.chatId)
                .putData("senderId", event.senderId.toString())
                .build()
        }
    }

    private fun sendBatch(batch: List<Message>) {
        try {
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
        } catch (e: Exception) {
            log.error("Failed to send FCM batch (size={})", batch.size, e)
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 500
    }
}
