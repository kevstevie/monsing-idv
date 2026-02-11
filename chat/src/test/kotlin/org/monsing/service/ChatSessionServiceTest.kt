package org.monsing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelaySubscriber
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator

class ChatSessionServiceTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var redisChatRelaySubscriber: RedisChatRelaySubscriber
    private lateinit var chatSessionService: ChatSessionService

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        redisChatRelaySubscriber = mockk(relaxed = true)
        chatSessionService = ChatSessionService(localSessionStorage, redisChatRelaySubscriber)
    }

    @Test
    fun `saveSession - 첫 세션이면 Redis subscribe 호출`() {
        val session = mockk<WebSocketSession>()
        every { localSessionStorage.getSessionByMemberId(1L) } returns null

        chatSessionService.saveSession(1L, "device-1", session)

        verify { localSessionStorage.saveSession(1L, "device-1", match { it is ConcurrentWebSocketSessionDecorator }) }
        verify { redisChatRelaySubscriber.subscribe(1L) }
    }

    @Test
    fun `saveSession - 빈 세션셋이면 Redis subscribe 호출`() {
        val session = mockk<WebSocketSession>()
        every { localSessionStorage.getSessionByMemberId(1L) } returns emptySet()

        chatSessionService.saveSession(1L, "device-1", session)

        verify { localSessionStorage.saveSession(1L, "device-1", match { it is ConcurrentWebSocketSessionDecorator }) }
        verify { redisChatRelaySubscriber.subscribe(1L) }
    }

    @Test
    fun `saveSession - 이미 세션 있으면 subscribe 미호출`() {
        val session = mockk<WebSocketSession>()
        val existingSession = mockk<WebSocketSession>()
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(existingSession)

        chatSessionService.saveSession(1L, "device-2", session)

        verify { localSessionStorage.saveSession(1L, "device-2", match { it is ConcurrentWebSocketSessionDecorator }) }
        verify(exactly = 0) { redisChatRelaySubscriber.subscribe(any()) }
    }

    @Test
    fun `removeSession - 마지막 세션이면 unsubscribe 호출`() {
        every { localSessionStorage.getSessionByMemberId(1L) } returns null

        chatSessionService.removeSession(1L, "device-1")

        verify { localSessionStorage.removeSession(1L, "device-1") }
        verify { redisChatRelaySubscriber.unsubscribe(1L) }
    }

    @Test
    fun `removeSession - 빈 세션셋이면 unsubscribe 호출`() {
        every { localSessionStorage.getSessionByMemberId(1L) } returns emptySet()

        chatSessionService.removeSession(1L, "device-1")

        verify { localSessionStorage.removeSession(1L, "device-1") }
        verify { redisChatRelaySubscriber.unsubscribe(1L) }
    }

    @Test
    fun `removeSession - 세션 남아있으면 unsubscribe 미호출`() {
        val remainingSession = mockk<WebSocketSession>()
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(remainingSession)

        chatSessionService.removeSession(1L, "device-1")

        verify { localSessionStorage.removeSession(1L, "device-1") }
        verify(exactly = 0) { redisChatRelaySubscriber.unsubscribe(any()) }
    }
}
