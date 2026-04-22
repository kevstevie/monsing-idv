package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageReceived
import org.monsing.chat.MessageReceivedRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Service
class ChatMessageHandler(
    private val objectMapper: ObjectMapper,
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val memberChatRepository: MemberChatRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val messageRepository: MessageRepository,
    private val messageDeliveryRepository: MessageDeliveryRepository,
    private val messageIdStrategy: MessageIdStrategy,
    private val messageReceivedRepository: MessageReceivedRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val sendExecutor = ThreadPoolExecutor(
        WORKER_COUNT, WORKER_COUNT,
        0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        ThreadPoolExecutor.AbortPolicy()
    )

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down message send executor")
        sendExecutor.shutdown()
        if (!sendExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            log.warn("Shutdown timeout, forcing cancellation")
            sendExecutor.shutdownNow()
        }
    }

    fun handleMessage(senderId: Long, dto: MessageDto) {
        val clientMessageId = dto.clientMessageId

        if (clientMessageId != null) {
            val existing = messageReceivedRepository.findByIdOrNull(clientMessageId)
            if (existing != null) {
                sendAck(existing.messageId, clientMessageId, dto.chatId, senderId)
                return
            }
        }

        val msg = Message(chatId = dto.chatId, senderId = senderId, content = dto.content)
        messageIdStrategy.generateId(msg)
        val messageId = requireNotNull(msg.id)

        clientMessageId?.let {
            messageReceivedRepository.save(MessageReceived(it, messageId, senderId, dto.chatId))
            sendAck(messageId, it, dto.chatId, senderId)
        }

        messageRepository.save(msg)

        val receivers = memberChatRepository.findReceiverIdByChatId(dto.chatId, senderId)
        messageDeliveryRepository.saveAll(receivers.map { MessageDelivery(messageId = messageId, receiverId = it) })

        try {
            sendExecutor.execute { sendMessage(msg, receivers) }
        } catch (e: RejectedExecutionException) {
            throw MessageSendOverloadException(msg.chatId, e)
        }
    }

    private fun sendAck(messageId: String, clientMessageId: String, chatId: Long, senderId: Long) {
        val sessions = localSessionStorage.getSessionByMemberId(senderId) ?: return
        val ackFrame = SendAckFrame(clientMessageId = clientMessageId, messageId = messageId, chatId = chatId)
        val payload = TextMessage(objectMapper.writeValueAsString(ackFrame))
        sessions.forEach { sendToSession(it, payload) }
    }

    fun relayMessage(receiverId: Long, message: Message) {
        val sessions = localSessionStorage.getSessionByMemberId(receiverId) ?: return
        val payload = message.toPayload()
        sessions.forEach { sendToSession(it, payload) }
    }

    private fun sendMessage(message: Message, receivers: List<Long>) {
        val payload = message.toPayload()
        for (receiver in receivers) {
            deliverToReceiver(receiver, message, payload)
        }
    }

    private fun deliverToReceiver(
        receiver: Long,
        message: Message,
        payload: TextMessage
    ) {
        val sessions = localSessionStorage.getSessionByMemberId(receiver)
            ?.takeIf { it.isNotEmpty() }

        if (sessions != null) {
            val anyDelivered = sessions.any { sendToSession(it, payload) }
            if (!anyDelivered) {
                eventPublisher.publishEvent(
                    ChatMessageNotDeliveredEvent(
                        receiverId = receiver,
                        chatId = message.chatId,
                        senderId = message.senderId,
                        content = message.content
                    )
                )
            }
        } else {
            val delivered = redisChatRelayPublisher.publishToUser(receiver, message)
            if (!delivered) {
                eventPublisher.publishEvent(
                    ChatMessageNotDeliveredEvent(
                        receiverId = receiver,
                        chatId = message.chatId,
                        senderId = message.senderId,
                        content = message.content
                    )
                )
            }
        }
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

    companion object {
        private const val WORKER_COUNT = 4
        private const val QUEUE_CAPACITY = 10_000
        private const val SHUTDOWN_TIMEOUT_SEC = 30L
    }
}
