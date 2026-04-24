package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.session.LocalSessionStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Component
class AckSender(
    private val localSessionStorage: LocalSessionStorage,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun sendOrThrow(senderId: Long, chatId: Long, messageId: String, clientMessageId: String) {
        val sessions = localSessionStorage.getSessionByMemberId(senderId)
            ?.takeIf { it.isNotEmpty() }
            ?: throw AckDeliveryFailedException(senderId, chatId, messageId, clientMessageId)

        val payload = TextMessage(
            objectMapper.writeValueAsString(
                SendAckFrame(clientMessageId = clientMessageId, messageId = messageId, chatId = chatId)
            )
        )

        val anyDelivered = sessions.any { sendToSession(it, payload) }
        if (!anyDelivered) {
            throw AckDeliveryFailedException(senderId, chatId, messageId, clientMessageId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendToSession(session: WebSocketSession, payload: TextMessage): Boolean {
        return try {
            session.sendMessage(payload)
            true
        } catch (e: Exception) {
            log.warn("Failed to send ACK to session {}: {}, removing stale session", session.id, e.message)
            localSessionStorage.removeSession(session)
            false
        }
    }
}
