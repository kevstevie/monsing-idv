package org.monsing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.SessionHealthMonitor.Companion.LAST_PONG_AT
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession

class SessionHealthMonitorTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var monitor: SessionHealthMonitor

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        monitor = SessionHealthMonitor(localSessionStorage)
    }

    @Test
    fun `tick - 최근 pong을 받은 열린 세션에는 ping을 전송한다`() {
        val session = openSession(lastPongAt = System.currentTimeMillis())
        every { localSessionStorage.allSessions() } returns listOf(session)

        monitor.tick()

        verify { session.sendMessage(any<PingMessage>()) }
        verify(exactly = 0) { session.close(any()) }
    }

    @Test
    fun `tick - pong 응답이 없는 세션은 종료한다`() {
        val staleTime = System.currentTimeMillis() - (SessionHealthMonitor.PONG_TIMEOUT_MS + 1_000)
        val session = openSession(lastPongAt = staleTime)
        every { localSessionStorage.allSessions() } returns listOf(session)

        monitor.tick()

        verify { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
        verify(exactly = 0) { session.sendMessage(any<PingMessage>()) }
    }

    @Test
    fun `tick - ping 전송 실패 시 세션을 종료한다`() {
        val session = openSession(lastPongAt = System.currentTimeMillis())
        every { session.sendMessage(any<PingMessage>()) } throws RuntimeException("boom")
        every { localSessionStorage.allSessions() } returns listOf(session)

        monitor.tick()

        verify { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
    }

    @Test
    fun `tick - 닫힌 세션은 건너뛴다`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns false
        every { localSessionStorage.allSessions() } returns listOf(session)

        monitor.tick()

        verify(exactly = 0) { session.sendMessage(any<PingMessage>()) }
        verify(exactly = 0) { session.close(any()) }
    }

    @Test
    fun `tick - LAST_PONG_AT 속성이 없는 세션은 현재 시각으로 초기화한다`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        val attributes = mutableMapOf<String, Any>()
        every { session.isOpen } returns true
        every { session.attributes } returns attributes
        every { localSessionStorage.allSessions() } returns listOf(session)

        monitor.tick()

        assert(attributes[LAST_PONG_AT] is Long) { "LAST_PONG_AT should be initialized" }
        verify { session.sendMessage(any<PingMessage>()) }
    }

    private fun openSession(lastPongAt: Long): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.attributes } returns mutableMapOf<String, Any>(LAST_PONG_AT to lastPongAt)
        return session
    }
}
