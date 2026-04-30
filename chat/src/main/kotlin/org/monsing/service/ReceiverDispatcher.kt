package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.Message
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Component
class ReceiverDispatcher(
    private val objectMapper: ObjectMapper,
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val messageDeliveryRepository: MessageDeliveryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun dispatchAll(receiverIds: Collection<Long>, message: Message) {
        if (receiverIds.isEmpty()) return
        val payload = message.toPayload()

        val remoteReceiverIds = receiverIds.filterNot { deliverLocally(it, payload) }
        if (remoteReceiverIds.isEmpty()) return

        val messageId = requireNotNull(message.id)
        messageDeliveryRepository
            .findAllByMessageIdAndReceiverIdIn(messageId, remoteReceiverIds)
            .forEach { it.transitionTo(MessageStatus.RELAY_PENDING) }
        val published = redisChatRelayPublisher.publishRelayBatch(remoteReceiverIds, message)
        if (!published) {
            log.warn(
                "Relay publish failed: messageId={} batchSize={} (will be retried by RELAY_PENDING scan)",
                messageId,
                remoteReceiverIds.size
            )
        }
    }

    fun relay(receiverId: Long, message: Message) {
        val sessions = localSessionStorage.getSessionByMemberId(receiverId) ?: return
        val payload = message.toPayload()
        sessions.forEach { sendToSession(it, payload) }
    }

    private fun deliverLocally(receiverId: Long, payload: TextMessage): Boolean {
        val sessions = localSessionStorage.getSessionByMemberId(receiverId) ?: return false
        return sessions.any { sendToSession(it, payload) }
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
