package org.monsing.chat.session

import java.util.concurrent.ConcurrentSkipListMap
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

@Component
class LocalSessionStorage(
    private val storage: ConcurrentSkipListMap<String, WebSocketSession> = ConcurrentSkipListMap(),
) {

    private val log = LoggerFactory.getLogger(javaClass)

    val size: Int get() = storage.size

    fun saveSession(memberId: Long, deviceId: String, session: WebSocketSession) {
        storage[createKey(memberId, deviceId)] = session
    }

    fun getSessionByMemberId(memberId: Long): Set<WebSocketSession>? {
        val prefix = "$memberId:"
        return storage.tailMap(prefix)
            .entries
            .takeWhile { it.key.startsWith(prefix) }
            .map { it.value }
            .toSet()
            .ifEmpty { null }
    }

    fun removeSession(memberId: Long, deviceId: String) {
        storage.remove(createKey(memberId, deviceId))
    }

    fun removeSession(session: WebSocketSession) {
        storage.entries.removeIf { it.value === session }
    }

    fun allSessions(): List<WebSocketSession> = storage.values.toList()

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    fun evictDeadSessions() {
        var count = 0
        allSessions().forEach { session ->
            if (!session.isOpen) {
                removeSession(session)
                count++
            }
        }
        if (count > 0) {
            log.info("Evicted {} dead sessions", count)
        }
    }

    private fun createKey(memberId: Long, deviceId: String): String {
        return "$memberId:$deviceId"
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
