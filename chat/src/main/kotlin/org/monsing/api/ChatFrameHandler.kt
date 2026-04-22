package org.monsing.api

import org.monsing.service.ChatMessageHandler
import org.monsing.service.MessageSendOverloadException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

@Component
class ChatFrameHandler(
    private val chatMessageHandler: ChatMessageHandler
) : InboundFrameHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun canHandle(frame: InboundFrame): Boolean = frame is InboundFrame.Chat

    override fun handle(session: WebSocketSession, memberId: Long, frame: InboundFrame) {
        val chat = frame as InboundFrame.Chat
        try {
            chatMessageHandler.handleMessage(memberId, chat.message)
        } catch (e: MessageSendOverloadException) {
            log.warn("Message rejected for sender={}: {}", memberId, e.message)
            session.sendMessage(TextMessage(ERROR_OVERLOAD))
        }
    }

    companion object {
        private const val ERROR_OVERLOAD = """{"error":"SERVER_BUSY","message":"Please retry"}"""
    }
}
