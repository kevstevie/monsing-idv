package org.monsing.api

import org.monsing.service.ChatSessionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val chatSessionService: ChatSessionService,
    private val inboundFrameParser: InboundFrameParser,
    private val frameHandlers: List<InboundFrameHandler>
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.saveSession(memberMetadata.memberId, memberMetadata.deviceId, session)
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val memberId = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata).memberId
        val frame = inboundFrameParser.parse(message.payload as String) ?: return
        val handler = frameHandlers.firstOrNull { it.canHandle(frame) } ?: run {
            log.warn("No handler found for frame {} from memberId={}", frame::class.simpleName, memberId)
            return
        }
        handler.handle(session, memberId, frame)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.removeSession(memberMetadata.memberId, memberMetadata.deviceId)
    }
}
