package org.monsing.api

import io.kotest.assertions.throwables.shouldThrowAny
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.service.ChatMessageHandler
import org.monsing.service.ChatSessionService
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketHandlerTest {

    private lateinit var chatMessageHandler: ChatMessageHandler
    private lateinit var chatSessionService: ChatSessionService
    private lateinit var handler: WebSocketHandler

    @BeforeEach
    fun setUp() {
        chatMessageHandler = mockk(relaxed = true)
        chatSessionService = mockk(relaxed = true)
        handler = WebSocketHandler(chatMessageHandler, chatSessionService)
    }

    @Test
    fun `afterConnectionEstablished - 세션 저장을 위임한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")

        handler.afterConnectionEstablished(session)

        verify { chatSessionService.saveSession(1L, "device-1", session) }
    }

    @Test
    fun `handleMessage - 메시지 처리를 위임한다`() {
        val session = mockSession(memberId = 1L, deviceId = "device-1")
        val message = TextMessage("""{"chatId":"chat-1","content":"hello"}""")

        handler.handleMessage(session, message)

        verify { chatMessageHandler.handleMessage(1L, message) }
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
        val message = TextMessage("""{"chatId":"chat-1","content":"hello"}""")

        shouldThrowAny {
            handler.handleMessage(session, message)
        }
    }

    private fun mockSession(memberId: Long, deviceId: String): WebSocketSession {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns mutableMapOf<String, Any>(
            MEMBER_METADATA to MemberMetadata(memberId, deviceId)
        )
        return session
    }
}
