package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.socket.WebSocketSession

class ReceiverDispatcherTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var redisChatRelayPublisher: RedisChatRelayPublisher
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var objectMapper: ObjectMapper
    private lateinit var dispatcher: ReceiverDispatcher

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        redisChatRelayPublisher = mockk(relaxed = true)
        eventPublisher = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        dispatcher = ReceiverDispatcher(
            objectMapper, localSessionStorage, redisChatRelayPublisher, eventPublisher
        )
    }

    @Test
    fun `dispatch - 로컬 세션이 있으면 WebSocket 송신`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { session.sendMessage(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `dispatch - 로컬 세션 송신 실패 시 stale 제거 + NotDeliveredEvent 발행`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)
        every { session.sendMessage(any()) } throws java.io.IOException("boom")
        every { localSessionStorage.removeSession(session) } just runs

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { localSessionStorage.removeSession(session) }
        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `dispatch - 로컬 세션 없으면 Redis publish, 실패 시 NotDeliveredEvent`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } returns false

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { eventPublisher.publishEvent(match<Any> { it is ChatMessageNotDeliveredEvent }) }
    }

    @Test
    fun `dispatch - Redis publish 성공 시 NotDeliveredEvent 발행 안 함`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        every { redisChatRelayPublisher.publishToUser(2L, any()) } returns true

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `relay - 로컬 세션이 있으면 WebSocket 송신`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)

        dispatcher.relay(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { session.sendMessage(any()) }
    }

    @Test
    fun `relay - 로컬 세션 없으면 아무 동작 안 함`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null

        dispatcher.relay(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }
}
