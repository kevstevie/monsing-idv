package org.monsing.service

import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AckHandler(
    private val messageDeliveryRepository: MessageDeliveryRepository
) {

    @Transactional
    fun handleAck(memberId: Long, messageId: String) {
        val delivery = messageDeliveryRepository.findByMessageIdAndReceiverId(messageId, memberId) ?: return
        delivery.transitionTo(MessageStatus.COMPLETE)
    }
}
