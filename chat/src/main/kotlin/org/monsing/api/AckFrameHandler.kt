package org.monsing.api

import org.monsing.service.AckHandler
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class AckFrameHandler(
    private val ackHandler: AckHandler
) : InboundFrameHandler {

    override fun canHandle(frame: InboundFrame): Boolean = frame is InboundFrame.Ack

    override fun handle(session: WebSocketSession, memberId: Long, frame: InboundFrame) {
        val ack = frame as InboundFrame.Ack
        ackHandler.handleAck(memberId, ack.messageId)
    }
}
