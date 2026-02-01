package org.monsing.service

import org.monsing.chat.session.LocalSessionStorage
import org.monsing.service.relay.RedisChatRelaySubscriber
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession

@Service
class ChatSessionService(
    private val localSessionStorage: LocalSessionStorage,
    private val redisChatRelaySubscriber: RedisChatRelaySubscriber
) {

    fun saveSession(memberId: Long, deviceId: String, session: WebSocketSession) {
        val previousSessionCount = localSessionStorage.getSessionByMemberId(memberId)?.size ?: 0

        localSessionStorage.saveSession(memberId, deviceId, session)

        if (previousSessionCount == 0) {
            redisChatRelaySubscriber.subscribe(memberId)
        }
    }

    fun removeSession(memberId: Long, deviceId: String) {
        localSessionStorage.removeSession(memberId, deviceId)

        val remainingSessions = localSessionStorage.getSessionByMemberId(memberId)?.size ?: 0
        if (remainingSessions == 0) {
            redisChatRelaySubscriber.unsubscribe(memberId)
        }
    }
}
