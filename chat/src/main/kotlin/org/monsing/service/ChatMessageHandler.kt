package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession

@Service
class ChatMessageHandler(
    private val objectMapper: ObjectMapper,
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val memberChatRepository: MemberChatRepository,
    private val eventPublisher: ApplicationEventPublisher
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

    fun handleMessage(senderId: Long, message: WebSocketMessage<*>) {
        val dto = objectMapper.readValue(message.payload as String, MessageDto::class.java)

        val msg = Message(chatId = dto.chatId, senderId = senderId, content = dto.content)

        eventPublisher.publishEvent(MessageCreatedEvent(msg))

        try {
            sendExecutor.execute { sendMessage(msg) }
        } catch (e: RejectedExecutionException) {
            throw MessageSendOverloadException(msg.chatId, e)
        }
    }

    fun relayMessage(receiverId: Long, message: Message) {
        val sessions = localSessionStorage.getSessionByMemberId(receiverId)
            ?: return
        val payload = message.toPayload()
        sessions.forEach { sendToSession(it, payload) }
    }

    private fun sendMessage(message: Message) {
        val receivers = memberChatRepository.findReceiverIdByChatId(
            message.chatId,
            message.senderId
        )
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
            sessions.forEach { sendToSession(it, payload) }
        } else {
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

    @Suppress("TooGenericExceptionCaught")
    private fun sendToSession(session: WebSocketSession, payload: TextMessage) {
        try {
            session.sendMessage(payload)
        } catch (e: Exception) {
            log.warn(
                "Failed to send to session {}: {}",
                session.id,
                e.message
            )
        }
    }

    private fun Message.toPayload() = TextMessage(objectMapper.writeValueAsString(this))

    companion object {
        private const val WORKER_COUNT = 4
        private const val QUEUE_CAPACITY = 10_000
        private const val SHUTDOWN_TIMEOUT_SEC = 30L
    }
}
