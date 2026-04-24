package org.monsing.service

import org.monsing.chat.Message
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageReceived
import org.monsing.chat.MessageReceivedRepository
import org.monsing.chat.MessageRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MessageInboxService(
    private val messageRepository: MessageRepository,
    private val messageReceivedRepository: MessageReceivedRepository,
    private val messageIdStrategy: MessageIdStrategy,
    private val ackSender: AckSender
) {

    fun resendAckIfDuplicate(senderId: Long, dto: MessageDto): Boolean {
        val cid = dto.clientMessageId ?: return false
        val existing = messageReceivedRepository.findByIdOrNull(cid) ?: return false
        ackSender.sendOrThrow(senderId, dto.chatId, existing.messageId, cid)
        return true
    }

    @Transactional
    fun persistAndAck(senderId: Long, dto: MessageDto): Message {
        val message = Message(chatId = dto.chatId, senderId = senderId, content = dto.content)
        messageIdStrategy.generateId(message)
        val messageId = requireNotNull(message.id)

        messageRepository.save(message)

        dto.clientMessageId?.let { cid ->
            messageReceivedRepository.save(
                MessageReceived(
                    clientMessageId = cid,
                    messageId = messageId,
                    senderId = senderId,
                    chatId = dto.chatId
                )
            )
            ackSender.sendOrThrow(senderId, dto.chatId, messageId, cid)
        }

        return message
    }
}
