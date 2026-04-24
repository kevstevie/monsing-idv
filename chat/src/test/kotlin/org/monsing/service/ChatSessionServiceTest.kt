package org.monsing.service

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.session.LocalSessionStorage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator

class ChatSessionServiceTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var chatSessionService: ChatSessionService

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        chatSessionService = ChatSessionService(localSessionStorage)
    }

    @Test
    fun `saveSession - 세션을 ConcurrentWebSocketSessionDecorator로 감싸 저장한다`() {
        val session = mockk<WebSocketSession>()

        chatSessionService.saveSession(1L, "device-1", session)

        verify { localSessionStorage.saveSession(1L, "device-1", match { it is ConcurrentWebSocketSessionDecorator }) }
    }

    @Test
    fun `removeSession - storage에 위임한다`() {
        chatSessionService.removeSession(1L, "device-1")

        verify { localSessionStorage.removeSession(1L, "device-1") }
    }
}
