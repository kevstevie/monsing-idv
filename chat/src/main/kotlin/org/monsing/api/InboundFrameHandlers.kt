package org.monsing.api

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class InboundFrameHandlers(
    private val handlers: List<InboundFrameHandler>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun dispatch(session: WebSocketSession, memberId: Long, frame: InboundFrame) {
        val handler = handlers.firstOrNull { it.canHandle(frame) } ?: run {
            log.warn("No handler found for frame {} from memberId={}", frame::class.simpleName, memberId)
            return
        }
        handler.handle(session, memberId, frame)
    }
}
