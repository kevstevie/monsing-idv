package org.monsing.api

import org.monsing.service.AckHandler
import org.monsing.service.ChatMessageHandler
import org.monsing.service.MessageDto
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

@Controller
class StompChatController(
    private val chatMessageHandler: ChatMessageHandler,
    private val ackHandler: AckHandler
) {

    @MessageMapping("/chat")
    fun handleChat(principal: ChatPrincipal, @Payload dto: MessageDto) {
        chatMessageHandler.handleMessage(principal.memberId, dto)
    }

    @MessageMapping("/ack")
    fun handleAck(principal: ChatPrincipal, @Payload payload: AckPayload) {
        ackHandler.handleAck(principal.memberId, payload.messageId)
    }
}
