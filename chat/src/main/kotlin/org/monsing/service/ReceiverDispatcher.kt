package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.Message
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Component
class ReceiverDispatcher(
    private val objectMapper: ObjectMapper,
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun dispatch(receiverId: Long, message: Message) {
        val payload = message.toPayload()
        val sessions = localSessionStorage.getSessionByMemberId(receiverId)?.takeIf { it.isNotEmpty() }

        val delivered =
            sessions?.any { sendToSession(it, payload) } ?: redisChatRelayPublisher.publishToUser(receiverId, message)

        if (!delivered) {
            eventPublisher.publishEvent(
                ChatMessageNotDeliveredEvent(
                    receiverId = receiverId,
                    chatId = message.chatId,
                    senderId = message.senderId,
                    content = message.content
                )
            )
        }
    }

    fun relay(receiverId: Long, message: Message) {
        val sessions = localSessionStorage.getSessionByMemberId(receiverId) ?: return
        val payload = message.toPayload()
        sessions.forEach { sendToSession(it, payload) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendToSession(session: WebSocketSession, payload: TextMessage): Boolean {
        return try {
            session.sendMessage(payload)
            true
        } catch (e: Exception) {
            log.warn("Failed to send to session {}: {}, removing stale session", session.id, e.message)
            localSessionStorage.removeSession(session)
            false
        }
    }

    private fun Message.toPayload() = TextMessage(objectMapper.writeValueAsString(this))
}
