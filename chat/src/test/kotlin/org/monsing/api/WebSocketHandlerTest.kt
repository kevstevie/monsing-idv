package org.monsing.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrowAny
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.service.AckHandler
import org.monsing.service.ChatMessageHandler
import org.monsing.service.ChatSessionService
import org.monsing.service.MessageDto
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketHandlerTest {

    private lateinit var chatMessageHandler: ChatMessageHandler
    private lateinit var chatSessionService: ChatSessionService
    private lateinit var ackHandler: AckHandler
    private lateinit var handler: WebSocketHandler

    @BeforeEach
    fun setUp() {
        chatMessageHandler = mockk(relaxed = true)
        chatSessionService = mockk(relaxed = true)
        ackHandler = mockk(relaxed = true)
        handler = WebSocketHandler(
            chatSessionService,
            InboundFrameParser(createObjectMapper()),
            listOf(
                ChatFrameHandler(chatMessageHandler),
                AckFrameHandler(ackHandler)
            )
        )
    }

    @Test
    fun `afterConnectionEstablished - 세션 저장을 위임한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")

        handler.afterConnectionEstablished(session)

        verify { chatSessionService.saveSession(1L, "device-1", session) }
    }

    @Test
    fun `handleMessage - CHAT 타입을 chatMessageHandler로 라우팅한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")
        val message = TextMessage("""{"type":"CHAT","chatId":1,"content":"hello"}""")

        handler.handleMessage(session, message)

        verify { chatMessageHandler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello")) }
    }

    @Test
    fun `handleMessage - type 없는 프레임은 CHAT으로 처리한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")
        val message = TextMessage("""{"chatId":1,"content":"hello"}""")

        handler.handleMessage(session, message)

        verify { chatMessageHandler.handleMessage(1L, MessageDto(chatId = 1L, content = "hello")) }
    }

    @Test
    fun `handleMessage - ACK 타입을 ackHandler로 라우팅한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")
        val message = TextMessage("""{"type":"ACK","messageId":"msg-123"}""")

        handler.handleMessage(session, message)

        verify { ackHandler.handleAck(1L, "msg-123") }
        verify(exactly = 0) { chatMessageHandler.handleMessage(any(), any()) }
    }

    @Test
    fun `handleMessage - ACK messageId 누락 시 무시한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")
        val message = TextMessage("""{"type":"ACK"}""")

        handler.handleMessage(session, message)

        verify(exactly = 0) { ackHandler.handleAck(any(), any()) }
    }

    @Test
    fun `afterConnectionClosed - 세션 제거를 위임한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")

        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify { chatSessionService.removeSession(1L, "device-1") }
    }

    @Test
    fun `afterConnectionEstablished - MemberMetadata 없으면 예외 발생`() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns mutableMapOf()

        shouldThrowAny {
            handler.afterConnectionEstablished(session)
        }
    }

    @Test
    fun `handleMessage - MemberMetadata 없으면 예외 발생`() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns mutableMapOf()
        val message = TextMessage("""{"chatId":1,"content":"hello"}""")

        shouldThrowAny {
            handler.handleMessage(session, message)
        }
    }

    private fun mockSession(memberId: Long, deviceId: String): WebSocketSession {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns mutableMapOf<String, Any>(
            MEMBER_METADATA to MemberMetadata(memberId, deviceId)
        )
        every { session.sendMessage(any()) } returns Unit
        return session
    }

    private fun createObjectMapper(): ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
}
