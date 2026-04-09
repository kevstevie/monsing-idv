package org.monsing.service

import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.springframework.stereotype.Service

@Service
class AckHandler(
    private val messageDeliveryRepository: MessageDeliveryRepository
) {

    fun handleAck(memberId: Long, messageId: String) {
        messageDeliveryRepository.updateStatus(messageId, memberId, MessageStatus.SUCCESS)
    }
}
