package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageReceived
import org.monsing.chat.MessageReceivedRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class ChatMessageHandlerTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var redisChatRelayPublisher: RedisChatRelayPublisher
    private lateinit var memberChatRepository: MemberChatRepository
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var messageRepository: MessageRepository
    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var messageIdStrategy: MessageIdStrategy
    private lateinit var messageReceivedRepository: MessageReceivedRepository
    private lateinit var handler: ChatMessageHandler

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        redisChatRelayPublisher = mockk(relaxed = true)
        memberChatRepository = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        messageDeliveryRepository = mockk(relaxed = true)
        messageIdStrategy = mockk(relaxed = true)
        messageReceivedRepository = mockk(relaxed = true)

        every { messageRepository.save(any()) } answers { firstArg() }
        every { messageDeliveryRepository.save(any()) } answers { firstArg() }
        every { messageIdStrategy.generateId(any()) } answers {
            firstArg<Message>().id = "test-msg-id"
        }
        every { messageReceivedRepository.findById(any()) } returns null
        every { messageReceivedRepository.save(any()) } answers { firstArg() }

        handler = ChatMessageHandler(
            objectMapper = createObjectMapper(),
            localSessionStorage = localSessionStorage,
            redisChatRelayPublisher = redisChatRelayPublisher,
            memberChatRepository = memberChatRepository,
            eventPublisher = eventPublisher,
            messageRepository = messageRepository,
            messageDeliveryRepository = messageDeliveryRepository,
            messageIdStrategy = messageIdStrategy,
            messageReceivedRepository = messageReceivedRepository
        )
    }

    @AfterEach
    fun tearDown() {
        handler.shutdown()
    }

    // --- 기존 테스트 ---

    @Test
    fun `handleMessage - DB에 메시지를 동기 저장한다`() {
        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 1) { messageRepository.save(any()) }
    }

    @Test
    fun `handleMessage - 수신자별 MessageDelivery를 메시지 저장 시점에 동기 생성한다`() {
        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns listOf(2L, 3L)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 1) { messageRepository.save(any()) }
        verify(exactly = 1) {
            messageDeliveryRepository.saveAll(match { deliveries ->
                deliveries.size == 2 && deliveries.all { it.messageId == "test-msg-id" }
            })
        }
    }

    @Test
    fun `handleMessage - 오프라인 수신자에게 ChatMessageNotDeliveredEvent 발행`() {
        val latch = CountDownLatch(1)

        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } returns false
        every {
            eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent })
        } answers { latch.countDown() }

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "ChatMessageNotDeliveredEvent not published")
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `handleMessage - 큐 초과 시 MessageSendOverloadException 발생`() {
        handler.shutdown()

        shouldThrow<MessageSendOverloadException> {
            handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))
        }
    }

    @Test
    fun `deliverToReceiver - 세션 전송 실패 시 ChatMessageNotDeliveredEvent 발행`() {
        val latch = CountDownLatch(1)
        val staleSession = mockk<WebSocketSession>(relaxed = true)

        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(staleSession)
        every { staleSession.sendMessage(any()) } throws java.io.IOException("connection reset")
        every { localSessionStorage.removeSession(staleSession) } just runs
        every {
            eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent })
        } answers { latch.countDown() }

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "ChatMessageNotDeliveredEvent not published")
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `deliverToReceiver - 세션 전송 실패 시 stale session 제거`() {
        val latch = CountDownLatch(1)
        val staleSession = mockk<WebSocketSession>(relaxed = true)

        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(staleSession)
        every { staleSession.sendMessage(any()) } throws java.io.IOException("connection reset")
        every { localSessionStorage.removeSession(staleSession) } answers { latch.countDown() }
        every { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) } just runs

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "stale session not removed")
        verify(exactly = 1) { localSessionStorage.removeSession(staleSession) }
    }

    // --- Send ACK 새 테스트 ---

    @Test
    fun `handleMessage - clientMessageId가 있으면 MessageReceived를 저장한다`() {
        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verify(exactly = 1) { messageReceivedRepository.save(match { it.clientMessageId == "cid-1" && it.messageId == "test-msg-id" }) }
    }

    @Test
    fun `handleMessage - MessageReceived 저장 후 발신자에게 SEND_ACK를 전송한다`() {
        val senderSession = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(senderSession)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verify(exactly = 1) {
            senderSession.sendMessage(match { message ->
                message is TextMessage &&
                    message.payload.contains("SEND_ACK") &&
                    message.payload.contains("cid-1") &&
                    message.payload.contains("test-msg-id")
            })
        }
    }

    @Test
    fun `handleMessage - MessageReceived 저장, ACK 송신, Message 저장 순서를 보장한다`() {
        val senderSession = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(senderSession)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verifyOrder {
            messageReceivedRepository.save(any())
            senderSession.sendMessage(any())
            messageRepository.save(any())
        }
    }

    @Test
    fun `handleMessage - 동일 clientMessageId 재전송 시 Message를 중복 저장하지 않는다`() {
        val existing = MessageReceived(clientMessageId = "cid-1", messageId = "old-msg-id", senderId = 1L, chatId = 1L)
        every { messageReceivedRepository.findById("cid-1") } returns existing

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verify(exactly = 0) { messageRepository.save(any()) }
        verify(exactly = 0) { messageReceivedRepository.save(any()) }
    }

    @Test
    fun `handleMessage - 동일 clientMessageId 재전송 시 기존 messageId로 ACK를 재전송한다`() {
        val senderSession = mockk<WebSocketSession>(relaxed = true)
        val existing = MessageReceived(clientMessageId = "cid-1", messageId = "old-msg-id", senderId = 1L, chatId = 1L)
        every { messageReceivedRepository.findById("cid-1") } returns existing
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(senderSession)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verify(exactly = 1) {
            senderSession.sendMessage(match { message ->
                message is TextMessage && message.payload.contains("old-msg-id")
            })
        }
    }

    @Test
    fun `handleMessage - clientMessageId 없으면 MessageReceived 저장 안 함`() {
        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 0) { messageReceivedRepository.save(any()) }
        verify(exactly = 0) { messageReceivedRepository.findById(any()) }
        verify(exactly = 1) { messageRepository.save(any()) }
    }

    companion object {
        private const val LATCH_TIMEOUT_SEC = 5L

        private fun createObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
