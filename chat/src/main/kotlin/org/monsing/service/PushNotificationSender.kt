package org.monsing.service

interface PushNotificationSender {

    fun handle(event: ChatMessageNotDeliveredEvent)
}
