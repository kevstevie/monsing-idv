package org.monsing.service

import org.monsing.chat.session.LocalSessionStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession

@Component
class SessionHealthMonitor(
    private val localSessionStorage: LocalSessionStorage
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    fun tick() {
        val now = System.currentTimeMillis()
        for (session in localSessionStorage.allSessions()) {
            if (!session.isOpen) continue
            handleSession(session, now)
        }
    }

    private fun handleSession(session: WebSocketSession, now: Long) {
        val lastPongAt = session.attributes[LAST_PONG_AT] as? Long
            ?: now.also { session.attributes[LAST_PONG_AT] = it }

        if (now - lastPongAt > PONG_TIMEOUT_MS) {
            closeQuietly(session, "pong timeout")
            return
        }

        sendPing(session)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendPing(session: WebSocketSession) {
        try {
            session.sendMessage(PingMessage())
        } catch (e: Exception) {
            log.warn("Ping send failed, closing session: {}", e.message)
            closeQuietly(session, "ping send failed")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun closeQuietly(session: WebSocketSession, reason: String) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE)
        } catch (e: Exception) {
            log.debug("Session close failed ({}): {}", reason, e.message)
        }
    }

    companion object {
        const val LAST_PONG_AT = "last-pong-at"
        const val PING_INTERVAL_MS = 30_000L
        const val PONG_TIMEOUT_MS = 60_000L
    }
}
