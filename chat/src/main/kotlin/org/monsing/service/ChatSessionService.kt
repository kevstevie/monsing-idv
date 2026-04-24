package org.monsing.service

import org.monsing.chat.session.LocalSessionStorage
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator

@Service
class ChatSessionService(
    private val localSessionStorage: LocalSessionStorage
) {

    fun saveSession(memberId: Long, deviceId: String, session: WebSocketSession) {
        val decoratedSession = ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIME_LIMIT,
            BUFFER_SIZE_LIMIT
        )

        localSessionStorage.saveSession(memberId, deviceId, decoratedSession)
    }

    fun removeSession(memberId: Long, deviceId: String) {
        localSessionStorage.removeSession(memberId, deviceId)
    }

    companion object {
        private const val SEND_TIME_LIMIT = 5000
        private const val BUFFER_SIZE_LIMIT = 65536
    }
}
