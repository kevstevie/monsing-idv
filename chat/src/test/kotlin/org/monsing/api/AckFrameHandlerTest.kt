package org.monsing.api

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.monsing.service.AckHandler
import org.monsing.service.MessageDto
import org.springframework.web.socket.WebSocketSession

class AckFrameHandlerTest {

    private val ackHandler = mockk<AckHandler>(relaxed = true)
    private val handler = AckFrameHandler(ackHandler)

    @Test
    fun `Ack 프레임만 처리한다`() {
        handler.canHandle(InboundFrame.Ack("msg-1")) shouldBe true
        handler.canHandle(InboundFrame.Chat(MessageDto(1L, "hi"))) shouldBe false
    }

    @Test
    fun `handle 은 ackHandler 에 위임한다`() {
        val session = mockk<WebSocketSession>(relaxed = true)

        handler.handle(session, memberId = 7L, frame = InboundFrame.Ack("msg-1"))

        verify { ackHandler.handleAck(7L, "msg-1") }
    }
}
