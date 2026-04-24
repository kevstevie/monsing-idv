package org.monsing.api

import org.monsing.service.ChatMessageHandler
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class ChatFrameHandler(
    private val chatMessageHandler: ChatMessageHandler
) : InboundFrameHandler {

    override fun canHandle(frame: InboundFrame): Boolean = frame is InboundFrame.Chat

    override fun handle(session: WebSocketSession, memberId: Long, frame: InboundFrame) {
        val chat = frame as InboundFrame.Chat
        chatMessageHandler.handleMessage(memberId, chat.message)
    }
}
