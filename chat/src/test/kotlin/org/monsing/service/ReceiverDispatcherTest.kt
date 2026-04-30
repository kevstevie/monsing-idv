package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelayPublisher
import org.springframework.web.socket.WebSocketSession

class ReceiverDispatcherTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var redisChatRelayPublisher: RedisChatRelayPublisher
    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var dispatcher: ReceiverDispatcher

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        redisChatRelayPublisher = mockk(relaxed = true)
        messageDeliveryRepository = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        dispatcher = ReceiverDispatcher(
            objectMapper, localSessionStorage, redisChatRelayPublisher, messageDeliveryRepository
        )
    }

    @Test
    fun `dispatch - 로컬 세션이 있으면 WebSocket 송신만 하고 상태 변경하지 않음`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { session.sendMessage(any()) }
        verify(exactly = 0) { messageDeliveryRepository.findByMessageIdAndReceiverId(any(), any()) }
        verify(exactly = 0) { redisChatRelayPublisher.publishRelay(any(), any()) }
    }

    @Test
    fun `dispatch - 로컬 세션 송신 실패 시 stale 제거 후 RELAY_PENDING 전이 + broadcast publish`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)
        every { session.sendMessage(any()) } throws java.io.IOException("boom")
        every { localSessionStorage.removeSession(session) } just runs
        val delivery = MessageDelivery(messageId = "m-1", receiverId = 2L)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns delivery

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { localSessionStorage.removeSession(session) }
        delivery.status shouldBe MessageStatus.RELAY_PENDING
        verify(exactly = 1) { redisChatRelayPublisher.publishRelay(2L, any()) }
    }

    @Test
    fun `dispatch - 로컬 세션 없으면 RELAY_PENDING 전이 + broadcast publish`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        val delivery = MessageDelivery(messageId = "m-1", receiverId = 2L)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns delivery

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        delivery.status shouldBe MessageStatus.RELAY_PENDING
        verify(exactly = 1) { redisChatRelayPublisher.publishRelay(2L, any()) }
    }

    @Test
    fun `dispatch - delivery row 가 없으면 상태 변경 없이 broadcast publish 만`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns null

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        verify(exactly = 1) { redisChatRelayPublisher.publishRelay(2L, any()) }
    }

    @Test
    fun `dispatch - 이미 COMPLETE 인 delivery 는 RELAY_PENDING 으로 강등되지 않음`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        val delivery = MessageDelivery(messageId = "m-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.COMPLETE)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns delivery

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        delivery.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `dispatch - 이미 RELAY_PENDING 인 delivery 재호출 시 멱등 (status 보존)`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        val delivery = MessageDelivery(messageId = "m-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.RELAY_PENDING)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns delivery

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        delivery.status shouldBe MessageStatus.RELAY_PENDING
        verify(exactly = 1) { redisChatRelayPublisher.publishRelay(2L, any()) }
    }

    @Test
    fun `dispatch - 이미 FAILED 인 delivery 는 RELAY_PENDING 으로 silent skip`() {
        every { localSessionStorage.getSessionByMemberId(2L) } returns null
        val delivery = MessageDelivery(messageId = "m-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.FAILED)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("m-1", 2L) } returns delivery

        dispatcher.dispatch(2L, Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi"))

        delivery.status shouldBe MessageStatus.FAILED
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

        verify(exactly = 0) { redisChatRelayPublisher.publishRelay(any(), any()) }
    }
}
