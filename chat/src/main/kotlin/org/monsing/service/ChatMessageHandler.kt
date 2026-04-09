package org.monsing.service

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
import org.monsing.chat.MessageRepository
import org.monsing.service.relay.RedisChatRelayPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Service

@Service
class ChatMessageHandler(
    private val messagingTemplate: SimpMessagingTemplate,
    private val simpUserRegistry: SimpUserRegistry,
    private val redisChatRelayPublisher: RedisChatRelayPublisher,
    private val memberChatRepository: MemberChatRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val messageRepository: MessageRepository,
    private val messageDeliveryRepository: MessageDeliveryRepository,
    private val messageIdStrategy: MessageIdStrategy
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
        val msg = Message(chatId = dto.chatId, senderId = senderId, content = dto.content)
        messageIdStrategy.generateId(msg)

        val receivers = memberChatRepository.findReceiverIdByChatId(dto.chatId, senderId)

        messageRepository.save(msg)
        receivers.forEach { receiverId ->
            messageDeliveryRepository.save(MessageDelivery(messageId = requireNotNull(msg.id), receiverId = receiverId))
        }

        try {
            sendExecutor.execute { sendMessage(msg, receivers) }
        } catch (e: RejectedExecutionException) {
            throw MessageSendOverloadException(msg.chatId, e)
        }
    }

    fun relayMessage(receiverId: Long, message: Message) {
        val user = simpUserRegistry.getUser(receiverId.toString()) ?: return
        if (user.sessions.isNotEmpty()) {
            messagingTemplate.convertAndSendToUser(receiverId.toString(), CHAT_DESTINATION, message)
        }
    }

    private fun sendMessage(message: Message, receivers: List<Long>) {
        for (receiver in receivers) {
            deliverToReceiver(receiver, message)
        }
    }

    private fun deliverToReceiver(receiver: Long, message: Message) {
        val user = simpUserRegistry.getUser(receiver.toString())

        if (user != null && user.sessions.isNotEmpty()) {
            messagingTemplate.convertAndSendToUser(receiver.toString(), CHAT_DESTINATION, message)
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

    companion object {
        private const val WORKER_COUNT = 4
        private const val QUEUE_CAPACITY = 10_000
        private const val SHUTDOWN_TIMEOUT_SEC = 30L
        private const val CHAT_DESTINATION = "/queue/chat"
    }
}
