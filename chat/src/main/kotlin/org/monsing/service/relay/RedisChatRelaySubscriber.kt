package org.monsing.service.relay

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.monsing.service.ChatMessageHandler
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component

@Component
class RedisChatRelaySubscriber(
    private val listenerContainer: RedisMessageListenerContainer,
    private val chatMessageHandler: ChatMessageHandler,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        listenerContainer.addMessageListener(
            createMessageListener(),
            ChannelTopic(RedisChatRelayPublisher.BROADCAST_CHANNEL)
        )
        log.info("Subscribed to broadcast relay channel: {}", RedisChatRelayPublisher.BROADCAST_CHANNEL)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createMessageListener(): MessageListener {
        return MessageListener { message, _ ->
            try {
                val envelope = objectMapper.readValue(message.body, RelayEnvelope::class.java)
                chatMessageHandler.relayMessage(envelope.receiverId, envelope.message)
            } catch (e: Exception) {
                log.error("Failed to process relay message", e)
            }
        }
    }
}
