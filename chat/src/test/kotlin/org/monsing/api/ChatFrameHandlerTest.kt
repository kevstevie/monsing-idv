package org.monsing.api

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.monsing.service.ChatMessageHandler
import org.monsing.service.MessageDto
import org.monsing.service.MessageSendOverloadException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class ChatFrameHandlerTest {

    private val chatMessageHandler = mockk<ChatMessageHandler>(relaxed = true)
    private val handler = ChatFrameHandler(chatMessageHandler)

    @Test
    fun `Chat 프레임만 처리한다`() {
        handler.canHandle(InboundFrame.Chat(MessageDto(1L, "hi"))) shouldBe true
        handler.canHandle(InboundFrame.Ack("msg-1")) shouldBe false
    }

    @Test
    fun `handle 은 chatMessageHandler 에 위임한다`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        val frame = InboundFrame.Chat(MessageDto(chatId = 1L, content = "hi"))

        handler.handle(session, memberId = 7L, frame = frame)

        verify { chatMessageHandler.handleMessage(7L, frame.message) }
    }

    @Test
    fun `Overload 예외 발생 시 SERVER_BUSY 에러 프레임을 전송한다`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        val frame = InboundFrame.Chat(MessageDto(chatId = 1L, content = "hi"))
        every { chatMessageHandler.handleMessage(any(), any()) } throws
            MessageSendOverloadException(1L, RuntimeException("queue full"))

        val sent = slot<TextMessage>()
        every { session.sendMessage(capture(sent)) } returns Unit

        handler.handle(session, memberId = 7L, frame = frame)

        sent.captured.payload shouldBe """{"error":"SERVER_BUSY","message":"Please retry"}"""
    }
}
