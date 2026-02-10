package org.monsing.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.mockk
import io.mockk.verify
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
            objectMapper = jacksonObjectMapper(),
            localSessionStorage = localSessionStorage,
            redisChatRelayPublisher = redisChatRelayPublisher,
            memberChatRepository = memberChatRepository,
            eventPublisher = eventPublisher
        )
    }

    @Test
    fun `handleMessage - MessageCreatedEvent 발행`() {
        val payload = """{"chatId":"chat-1","content":"hello"}"""

        handler.handleMessage(1L, TextMessage(payload))

        verify(exactly = 1) { eventPublisher.publishEvent(any<MessageCreatedEvent>()) }
    }

    @Test
    fun `handleMessage - 오프라인 수신자에게 ChatMessageSentEvent 발행`() {
        io.mockk.every {
            memberChatRepository.findReceiverIdByChatId("chat-1", 1L)
        } returns listOf(2L)
        io.mockk.every { localSessionStorage.getSessionByMemberId(2L) } returns null
        io.mockk.every { redisChatRelayPublisher.publishToUser(2L, any()) } returns false

        val payload = """{"chatId":"chat-1","content":"hello"}"""

        handler.handleMessage(1L, TextMessage(payload))

        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageSentEvent }) }
    }
}
