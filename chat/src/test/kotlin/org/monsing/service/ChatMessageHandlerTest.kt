package org.monsing.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageRepository
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.user.SimpUser
import org.springframework.messaging.simp.user.SimpUserRegistry

class ChatMessageHandlerTest {

    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var simpUserRegistry: SimpUserRegistry
    private lateinit var redisChatRelayPublisher: RedisChatRelayPublisher
    private lateinit var memberChatRepository: MemberChatRepository
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var messageRepository: MessageRepository
    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var messageIdStrategy: MessageIdStrategy
    private lateinit var handler: ChatMessageHandler

    @BeforeEach
    fun setUp() {
        messagingTemplate = mockk(relaxed = true)
        simpUserRegistry = mockk(relaxed = true)
        redisChatRelayPublisher = mockk(relaxed = true)
        memberChatRepository = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        messageDeliveryRepository = mockk(relaxed = true)
        messageIdStrategy = mockk(relaxed = true)

        every { messageRepository.save(any()) } answers { firstArg() }
        every { messageDeliveryRepository.save(any()) } answers { firstArg() }
        every { messageIdStrategy.generateId(any()) } answers {
            firstArg<Message>().id = "test-msg-id"
        }

        handler = ChatMessageHandler(
            messagingTemplate = messagingTemplate,
            simpUserRegistry = simpUserRegistry,
            redisChatRelayPublisher = redisChatRelayPublisher,
            memberChatRepository = memberChatRepository,
            eventPublisher = eventPublisher,
            messageRepository = messageRepository,
            messageDeliveryRepository = messageDeliveryRepository,
            messageIdStrategy = messageIdStrategy
        )
    }

    @AfterEach
    fun tearDown() {
        handler.shutdown()
    }

    @Test
    fun `handleMessage - MongoDB에 메시지를 동기 저장한다`() {
        handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))

        verify(exactly = 1) { messageRepository.save(any()) }
    }

    @Test
    fun `handleMessage - 수신자별 MessageDelivery를 메시지 저장 시점에 동기 생성한다`() {
        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L, 3L)

        handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))

        verify(exactly = 1) { messageRepository.save(any()) }
        verify(exactly = 2) { messageDeliveryRepository.save(match<MessageDelivery> { it.messageId == "test-msg-id" }) }
    }

    @Test
    fun `deliverToReceiver - 온라인 수신자에게 SimpMessagingTemplate으로 전송한다`() {
        val latch = CountDownLatch(1)
        val onlineUser = mockk<SimpUser> {
            every { sessions } returns setOf(mockk())
        }

        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L)
        every { simpUserRegistry.getUser("2") } returns onlineUser
        every { messagingTemplate.convertAndSendToUser(eq("2"), eq("/queue/chat"), any()) } answers { latch.countDown() }

        handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "convertAndSendToUser not called")
        verify { messagingTemplate.convertAndSendToUser(eq("2"), eq("/queue/chat"), any()) }
    }

    @Test
    fun `deliverToReceiver - 오프라인 수신자는 Redis relay를 시도한다`() {
        val latch = CountDownLatch(1)

        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L)
        every { simpUserRegistry.getUser("2") } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } answers {
            latch.countDown()
            true
        }

        handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "Redis relay not called")
        verify { redisChatRelayPublisher.publishToUser(eq(2L), any()) }
    }

    @Test
    fun `handleMessage - 오프라인 수신자에게 ChatMessageNotDeliveredEvent 발행`() {
        val latch = CountDownLatch(1)

        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L)
        every { simpUserRegistry.getUser("2") } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } returns false
        every {
            eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent })
        } answers { latch.countDown() }

        handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "ChatMessageNotDeliveredEvent not published")
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `handleMessage - 큐 초과 시 MessageSendOverloadException 발생`() {
        handler.shutdown()

        io.kotest.assertions.throwables.shouldThrow<MessageSendOverloadException> {
            handler.handleMessage(1L, MessageDto(chatId = "chat-1", content = "hello"))
        }
    }

    @Test
    fun `relayMessage - 온라인 수신자에게 SimpMessagingTemplate으로 전송한다`() {
        val onlineUser = mockk<SimpUser> {
            every { sessions } returns setOf(mockk())
        }
        every { simpUserRegistry.getUser("2") } returns onlineUser

        val message = Message(chatId = "chat-1", senderId = 1L, content = "hello").also { it.id = "msg-1" }
        handler.relayMessage(2L, message)

        verify { messagingTemplate.convertAndSendToUser(eq("2"), eq("/queue/chat"), any()) }
    }

    @Test
    fun `relayMessage - 오프라인 수신자면 전송하지 않는다`() {
        every { simpUserRegistry.getUser("2") } returns null

        val message = Message(chatId = "chat-1", senderId = 1L, content = "hello").also { it.id = "msg-1" }
        handler.relayMessage(2L, message)

        verify(exactly = 0) { messagingTemplate.convertAndSendToUser(any(), any(), any<Any>()) }
    }

    companion object {
        private const val LATCH_TIMEOUT_SEC = 5L
    }
}
