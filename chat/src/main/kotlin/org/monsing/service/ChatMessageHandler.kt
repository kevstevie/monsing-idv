package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage

@Service
class ChatMessageHandler(
    private val objectMapper: ObjectMapper,
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val memberChatRepository: MemberChatRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun handleMessage(senderId: Long, message: WebSocketMessage<*>) {
        val dto = objectMapper.readValue(message.payload as String, MessageDto::class.java)

        val msg = Message(chatId = dto.chatId, senderId = senderId, content = dto.content)

        eventPublisher.publishEvent(MessageCreatedEvent(msg))

        sendMessage(msg)
    }

    fun relayMessage(receiverId: Long, message: Message) {
        localSessionStorage.getSessionByMemberId(receiverId)?.let { session ->
            session.forEach {
                it.sendMessage(message.toPayload())
            }
        }
    }

    private fun sendMessage(message: Message) {
        val receivers = memberChatRepository.findReceiverIdByChatId(message.chatId, message.senderId)

        for (receiver in receivers) {
            localSessionStorage.getSessionByMemberId(receiver)
                ?.takeIf { it.isNotEmpty() }
                ?.forEach { it.sendMessage(message.toPayload()) }
                ?: run {
                    val delivered = redisChatRelayPublisher.publishToUser(receiver, message)
                    if (!delivered) {
                        eventPublisher.publishEvent(
                            ChatMessageSentEvent(
                                receiverId = receiver,
                                chatId = message.chatId,
                                senderId = message.senderId,
                                content = message.content
                            )
                        )
                    }
                }
        }
    }

    private fun Message.toPayload() = TextMessage(objectMapper.writeValueAsString(this))
}
