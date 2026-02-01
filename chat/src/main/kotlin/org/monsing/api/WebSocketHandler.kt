package org.monsing.api

import org.monsing.service.ChatMessageHandler
import org.monsing.service.ChatSessionService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val chatMessageHandler: ChatMessageHandler,
    private val chatSessionService: ChatSessionService
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.saveSession(memberMetadata.memberId, memberMetadata.deviceId, session)
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val senderId = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata).memberId

        chatMessageHandler.handleMessage(senderId, message)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.removeSession(memberMetadata.memberId, memberMetadata.deviceId)
    }
}


