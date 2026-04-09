package org.monsing.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.monsing.api.ChatPrincipal
import org.monsing.service.relay.RedisChatRelaySubscriber
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
class StompSessionEventListener(
    private val redisChatRelaySubscriber: RedisChatRelaySubscriber
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sessionCounts = ConcurrentHashMap<Long, AtomicInteger>()

    @EventListener
    fun onSessionConnected(event: SessionConnectedEvent) {
        val memberId = (event.user as? ChatPrincipal)?.memberId ?: return
        val count = sessionCounts.computeIfAbsent(memberId) { AtomicInteger(0) }.incrementAndGet()
        if (count == 1) {
            log.info("First session connected for memberId={}, subscribing to Redis channel", memberId)
            redisChatRelaySubscriber.subscribe(memberId)
        }
    }

    @EventListener
    fun onSessionDisconnected(event: SessionDisconnectEvent) {
        val memberId = (event.user as? ChatPrincipal)?.memberId ?: return
        val counter = sessionCounts[memberId] ?: return
        val remaining = counter.decrementAndGet()
        if (remaining <= 0) {
            sessionCounts.remove(memberId)
            log.info("Last session disconnected for memberId={}, unsubscribing from Redis channel", memberId)
            redisChatRelaySubscriber.unsubscribe(memberId)
        }
    }
}
