package org.monsing.service

import org.monsing.chat.Message
import org.springframework.stereotype.Service

@Service
class ChatMessageHandler(
    private val messageInboxService: MessageInboxService,
    private val messageFanoutService: MessageFanoutService,
    private val receiverDispatcher: ReceiverDispatcher
) {

    fun handleMessage(senderId: Long, dto: MessageDto) {
        if (messageInboxService.resendAckIfDuplicate(senderId, dto)) return

        val msg = messageInboxService.persistAndAck(senderId, dto)
        val messageId = requireNotNull(msg.id)

        val receivers = messageFanoutService.persistDeliveries(dto.chatId, senderId, messageId)
        receiverDispatcher.dispatchAll(receivers, msg)
    }

    fun relayMessage(receiverId: Long, message: Message) {
        receiverDispatcher.relay(receiverId, message)
    }
}
