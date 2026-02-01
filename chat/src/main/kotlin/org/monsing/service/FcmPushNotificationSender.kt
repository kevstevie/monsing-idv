package org.monsing.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.monsing.alert.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
@Profile("!local")
class FcmPushNotificationSender(
    private val firebaseMessaging: FirebaseMessaging,
    private val fcmTokenRepository: FcmTokenRepository
) : PushNotificationSender {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    override fun handle(event: ChatMessageSentEvent) {
        val tokens = fcmTokenRepository.findToken(event.receiverId)
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens found for receiverId: {}", event.receiverId)
            return
        }

        val message = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle("새 메시지")
                    .setBody(event.content)
                    .build()
            )
            .putData("chatId", event.chatId)
            .putData("senderId", event.senderId.toString())
            .addAllTokens(tokens.toList())
            .build()

        try {
            val response = firebaseMessaging.sendEachForMulticast(message)
            if (response.failureCount > 0) {
                val tokenList = tokens.toList()
                response.responses.forEachIndexed { index, sendResponse ->
                    if (!sendResponse.isSuccessful) {
                        log.warn(
                            "FCM send failed for token: {}, error: {}",
                            tokenList[index], sendResponse.exception?.message
                        )
                    }
                }
            }
            log.debug("FCM push sent: success={}, failure={}", response.successCount, response.failureCount)
        } catch (e: Exception) {
            log.error("Failed to send FCM push for receiverId: {}", event.receiverId, e)
        }
    }
}