package org.monsing.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.service.AckHandler
import org.monsing.service.ChatMessageHandler
import org.monsing.service.ChatSessionService
import org.monsing.service.MessageDto
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
    private val chatSessionService: ChatSessionService,
    private val ackHandler: AckHandler,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.saveSession(memberMetadata.memberId, memberMetadata.deviceId, session)
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        val memberId = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata).memberId
        val payload = message.payload as String
        val jsonNode = objectMapper.readTree(payload)
        val type = jsonNode.get("type")?.asText() ?: FRAME_TYPE_CHAT

        when (type) {
            FRAME_TYPE_ACK -> {
                val messageId = jsonNode.get("messageId")?.asText()
                if (messageId == null) {
                    log.warn("ACK frame missing messageId from memberId={}", memberId)
                    return
                }
                ackHandler.handleAck(memberId, messageId)
            }
            FRAME_TYPE_CHAT -> {
                try {
                    val dto = objectMapper.treeToValue(jsonNode, MessageDto::class.java)
                    chatMessageHandler.handleMessage(memberId, dto)
                } catch (e: MessageSendOverloadException) {
                    log.warn("Message rejected for sender={}: {}", memberId, e.message)
                    session.sendMessage(TextMessage(ERROR_OVERLOAD))
                }
            }
            else -> log.warn("Unknown frame type '{}' from memberId={}", type, memberId)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val memberMetadata = requireNotNull(session.attributes[MEMBER_METADATA] as MemberMetadata)
        chatSessionService.removeSession(memberMetadata.memberId, memberMetadata.deviceId)
    }

    companion object {
        private const val FRAME_TYPE_CHAT = "CHAT"
        private const val FRAME_TYPE_ACK = "ACK"
        private const val ERROR_OVERLOAD = """{"error":"SERVER_BUSY","message":"Please retry"}"""
    }
}
