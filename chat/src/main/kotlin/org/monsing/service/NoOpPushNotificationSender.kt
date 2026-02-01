package org.monsing.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@Profile("local")
class NoOpPushNotificationSender : PushNotificationSender {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    override fun handle(event: ChatMessageSentEvent) {
        log.info(
            "FCM push skipped (local): receiverId={}, chatId={}, senderId={}",
            event.receiverId, event.chatId, event.senderId
        )
    }
}