package org.monsing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message

class ChatMessageHandlerTest {

    private lateinit var messageInboxService: MessageInboxService
    private lateinit var messageFanoutService: MessageFanoutService
    private lateinit var receiverDispatcher: ReceiverDispatcher
    private lateinit var handler: ChatMessageHandler

    @BeforeEach
    fun setUp() {
        messageInboxService = mockk(relaxed = true)
        messageFanoutService = mockk(relaxed = true)
        receiverDispatcher = mockk(relaxed = true)

        every { messageInboxService.resendAckIfDuplicate(any(), any()) } returns false
        every { messageInboxService.persistAndAck(any(), any()) } answers {
            val senderId = firstArg<Long>()
            val dto = secondArg<MessageDto>()
            Message(id = "test-msg-id", chatId = dto.chatId, senderId = senderId, content = dto.content)
        }
        every { messageFanoutService.persistDeliveries(any(), any(), any()) } returns emptyList()

        handler = ChatMessageHandler(
            messageInboxService = messageInboxService,
            messageFanoutService = messageFanoutService,
            receiverDispatcher = receiverDispatcher
        )
    }

    @Test
    fun `handleMessage - T1 인박스 서비스에 저장+ACK를 위임한다`() {
        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 1) {
            messageInboxService.persistAndAck(
                senderId = 1L,
                dto = match { it.chatId == 1L && it.content == "hello" }
            )
        }
    }

    @Test
    fun `handleMessage - 수신자별 MessageDelivery를 Fanout 서비스에 위임한다`() {
        every {
            messageFanoutService.persistDeliveries(1L, 1L, "test-msg-id")
        } returns listOf(2L, 3L)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 1) { messageFanoutService.persistDeliveries(1L, 1L, "test-msg-id") }
    }

    @Test
    fun `handleMessage - Fanout이 반환한 수신자 각각에게 receiverDispatcher로 송신 위임`() {
        every {
            messageFanoutService.persistDeliveries(1L, 1L, "test-msg-id")
        } returns listOf(2L, 3L)

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello"))

        verify(exactly = 1) { receiverDispatcher.dispatch(2L, match { it.id == "test-msg-id" }) }
        verify(exactly = 1) { receiverDispatcher.dispatch(3L, match { it.id == "test-msg-id" }) }
    }

    @Test
    fun `handleMessage - T1 실패 시 Fanout과 송신은 실행되지 않는다`() {
        every {
            messageInboxService.persistAndAck(any(), any())
        } throws AckDeliveryFailedException(1L, 1L, "test-msg-id", "cid-1")

        assertThrows(AckDeliveryFailedException::class.java) {
            handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))
        }

        verify(exactly = 0) { messageFanoutService.persistDeliveries(any(), any(), any()) }
        verify(exactly = 0) { receiverDispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `handleMessage - 재전송 분기는 persistAndAck와 Fanout 위임을 스킵한다`() {
        every { messageInboxService.resendAckIfDuplicate(1L, any()) } returns true

        handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))

        verify(exactly = 0) { messageInboxService.persistAndAck(any(), any()) }
        verify(exactly = 0) { messageFanoutService.persistDeliveries(any(), any(), any()) }
        verify(exactly = 0) { receiverDispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `handleMessage - 재전송 분기 ACK 실패 시 예외 전파`() {
        every {
            messageInboxService.resendAckIfDuplicate(1L, any())
        } throws AckDeliveryFailedException(1L, 1L, "old-msg-id", "cid-1")

        assertThrows(AckDeliveryFailedException::class.java) {
            handler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello", clientMessageId = "cid-1"))
        }
    }

    @Test
    fun `relayMessage - receiverDispatcher relay에 위임한다`() {
        val msg = Message(id = "m-1", chatId = 1L, senderId = 1L, content = "hi")

        handler.relayMessage(2L, msg)

        verify(exactly = 1) { receiverDispatcher.relay(2L, msg) }
    }
}
