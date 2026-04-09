package org.monsing.api

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.service.AckHandler
import org.monsing.service.ChatMessageHandler
import org.monsing.service.MessageDto

class StompChatControllerTest {

    private lateinit var chatMessageHandler: ChatMessageHandler
    private lateinit var ackHandler: AckHandler
    private lateinit var controller: StompChatController

    @BeforeEach
    fun setUp() {
        chatMessageHandler = mockk(relaxed = true)
        ackHandler = mockk(relaxed = true)
        controller = StompChatController(chatMessageHandler, ackHandler)
    }

    @Test
    fun `handleChat - CHAT 메시지를 chatMessageHandler에 위임한다`() {
        val principal = ChatPrincipal(memberId = 1L)
        val dto = MessageDto(chatId = "chat-1", content = "hello")

        controller.handleChat(principal, dto)

        verify { chatMessageHandler.handleMessage(1L, dto) }
    }

    @Test
    fun `handleAck - ACK를 ackHandler에 위임한다`() {
        val principal = ChatPrincipal(memberId = 1L)
        val payload = AckPayload(messageId = "msg-123")

        controller.handleAck(principal, payload)

        verify { ackHandler.handleAck(1L, "msg-123") }
    }
}
