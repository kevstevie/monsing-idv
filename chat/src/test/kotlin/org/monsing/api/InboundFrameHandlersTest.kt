package org.monsing.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.monsing.service.MessageDto
import org.springframework.web.socket.WebSocketSession

class InboundFrameHandlersTest {

    private val session = mockk<WebSocketSession>(relaxed = true)

    @Test
    fun `dispatch - canHandle이 true인 첫 핸들러로 위임한다`() {
        val chatHandler = mockk<InboundFrameHandler>(relaxed = true)
        val ackHandler = mockk<InboundFrameHandler>(relaxed = true)
        every { chatHandler.canHandle(any()) } returns false
        every { ackHandler.canHandle(any()) } returns true

        val handlers = InboundFrameHandlers(listOf(chatHandler, ackHandler))
        val frame = InboundFrame.Ack("msg-1")

        handlers.dispatch(session, 1L, frame)

        verify { ackHandler.handle(session, 1L, frame) }
        verify(exactly = 0) { chatHandler.handle(any(), any(), any()) }
    }

    @Test
    fun `dispatch - canHandle이 true인 핸들러가 없으면 아무것도 호출하지 않는다`() {
        val handler = mockk<InboundFrameHandler>(relaxed = true)
        every { handler.canHandle(any()) } returns false

        val handlers = InboundFrameHandlers(listOf(handler))
        val frame = InboundFrame.Chat(MessageDto(chatId = 1L, content = "hi"))

        handlers.dispatch(session, 1L, frame)

        verify(exactly = 0) { handler.handle(any(), any(), any()) }
    }

    @Test
    fun `dispatch - 핸들러가 비어있어도 예외 없이 처리된다`() {
        val handlers = InboundFrameHandlers(emptyList())
        val frame = InboundFrame.Ack("msg-1")

        handlers.dispatch(session, 1L, frame)
    }
}
