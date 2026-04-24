package org.monsing.service.relay

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.Message
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisChatRelayPublisher(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("TooGenericExceptionCaught")
    fun publishRelay(receiverId: Long, message: Message): Boolean {
        val payload = objectMapper.writeValueAsString(RelayEnvelope(receiverId, message))

        return try {
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, payload)
            true
        } catch (e: Exception) {
            log.error("Failed to publish relay message: receiverId={}", receiverId, e)
            false
        }
    }

    companion object {
        const val BROADCAST_CHANNEL = "chat:relay"
    }
}
