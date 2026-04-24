package org.monsing.api

import org.monsing.service.ChatSessionService
import org.monsing.service.SessionHealthMonitor
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val chatSessionService: ChatSessionService,
    private val inboundFrameParser: InboundFrameParser,
    private val inboundFrameHandlers: InboundFrameHandlers
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        session.attributes[SessionHealthMonitor.LAST_PONG_AT] = System.currentTimeMillis()
        chatSessionService.saveSession(memberMetadata.memberId, memberMetadata.deviceId, session)
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        session.attributes[SessionHealthMonitor.LAST_PONG_AT] = System.currentTimeMillis()
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val memberId = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata).memberId
        val frame = inboundFrameParser.parse(message.payload) ?: return
        inboundFrameHandlers.dispatch(session, memberId, frame)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.removeSession(memberMetadata.memberId, memberMetadata.deviceId)
    }
}
