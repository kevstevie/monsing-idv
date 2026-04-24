package org.monsing.service

import org.monsing.chat.MemberChatRepository
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MessageFanoutService(
    private val memberChatRepository: MemberChatRepository,
    private val messageDeliveryRepository: MessageDeliveryRepository
) {

    @Transactional
    fun persistDeliveries(chatId: Long, senderId: Long, messageId: String): List<Long> {
        val receivers = memberChatRepository.findReceiverIdByChatId(chatId, senderId)
        if (receivers.isNotEmpty()) {
            messageDeliveryRepository.saveAll(
                receivers.map { MessageDelivery(messageId = messageId, receiverId = it) }
            )
        }
        return receivers
    }
}
