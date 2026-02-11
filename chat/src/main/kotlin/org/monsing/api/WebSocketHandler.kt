package org.monsing.api

import org.monsing.service.ChatMessageHandler
import org.monsing.service.ChatSessionService
import org.monsing.service.MessageSendOverloadException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler(
    private val chatMessageHandler: ChatMessageHandler,
    private val chatSessionService: ChatSessionService
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.saveSession(memberMetadata.memberId, memberMetadata.deviceId, session)
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val senderId = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata).memberId

        try {
            chatMessageHandler.handleMessage(senderId, message)
        } catch (e: MessageSendOverloadException) {
            log.warn("Message rejected for sender={}: {}", senderId, e.message)
            session.sendMessage(TextMessage(ERROR_OVERLOAD))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.removeSession(memberMetadata.memberId, memberMetadata.deviceId)
    }

    companion object {
        private const val ERROR_OVERLOAD = """{"error":"SERVER_BUSY","message":"Please retry"}"""
    }
}
