package org.monsing.api

import org.springframework.web.socket.WebSocketSession

interface InboundFrameHandler {
    fun canHandle(frame: InboundFrame): Boolean
    fun handle(session: WebSocketSession, memberId: Long, frame: InboundFrame)
}
