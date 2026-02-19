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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.socket.TextMessage

class ChatMessageHandlerTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var redisChatRelayPublisher: RedisChatRelayPublisher
    private lateinit var memberChatRepository: MemberChatRepository
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var handler: ChatMessageHandler

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        redisChatRelayPublisher = mockk(relaxed = true)
        memberChatRepository = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)

        handler = ChatMessageHandler(
            objectMapper = createObjectMapper(),
            localSessionStorage = localSessionStorage,
            redisChatRelayPublisher = redisChatRelayPublisher,
            memberChatRepository = memberChatRepository,
            eventPublisher = eventPublisher
        )
    }

    @AfterEach
    fun tearDown() {
        handler.shutdown()
    }

    @Test
    fun `handleMessage - MessageCreatedEvent 발행`() {
        val payload = """{"chatId":"chat-1","content":"hello"}"""

        handler.handleMessage(1L, TextMessage(payload))

        verify(exactly = 1) { eventPublisher.publishEvent(any<MessageCreatedEvent>()) }
    }

    @Test
    fun `handleMessage - 오프라인 수신자에게 ChatMessageSentEvent 발행`() {
        val latch = CountDownLatch(1)

        every {
            memberChatRepository.findReceiverIdByChatId("chat-1", 1L)
        } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } returns false
        every {
            eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent })
        } answers {
            latch.countDown()
        }

        val payload = """{"chatId":"chat-1","content":"hello"}"""

        handler.handleMessage(1L, TextMessage(payload))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "ChatMessageSentEvent not published")
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `handleMessage - 큐 초과 시 MessageSendOverloadException 발생`() {
        handler.shutdown()

        val payload = """{"chatId":"chat-1","content":"hello"}"""

        shouldThrow<MessageSendOverloadException> {
            handler.handleMessage(1L, TextMessage(payload))
        }
    }

    @Test
    fun `deliverToReceiver - 세션 전송 실패 시 FCM fallback 이벤트 발행`() {
        val latch = CountDownLatch(1)
        val staleSession = mockk<org.springframework.web.socket.WebSocketSession>(relaxed = true)

        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(staleSession)
        every { staleSession.sendMessage(any()) } throws java.io.IOException("connection reset")
        every { localSessionStorage.removeSession(staleSession) } just runs
        every {
            eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent })
        } answers { latch.countDown() }

        handler.handleMessage(1L, TextMessage("""{"chatId":"chat-1","content":"hello"}"""))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "ChatMessageNotDeliveredEvent not published")
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `deliverToReceiver - 세션 전송 실패 시 stale session 제거`() {
        val latch = CountDownLatch(1)
        val staleSession = mockk<org.springframework.web.socket.WebSocketSession>(relaxed = true)

        every { memberChatRepository.findReceiverIdByChatId("chat-1", 1L) } returns listOf(2L)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(staleSession)
        every { staleSession.sendMessage(any()) } throws java.io.IOException("connection reset")
        every { localSessionStorage.removeSession(staleSession) } answers { latch.countDown() }
        every { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) } just runs

        handler.handleMessage(1L, TextMessage("""{"chatId":"chat-1","content":"hello"}"""))

        assertTrue(latch.await(LATCH_TIMEOUT_SEC, TimeUnit.SECONDS), "stale session not removed")
        verify(exactly = 1) { localSessionStorage.removeSession(staleSession) }
    }

    companion object {
        private const val LATCH_TIMEOUT_SEC = 5L

        private fun createObjectMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
