package org.monsing.api

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.monsing.service.ChatMessageHandler
import org.monsing.service.MessageDto
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
}
