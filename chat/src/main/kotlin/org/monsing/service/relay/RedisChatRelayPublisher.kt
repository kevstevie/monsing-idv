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
    fun publishRelayBatch(receiverIds: List<Long>, message: Message): Boolean {
        if (receiverIds.isEmpty()) return true
        require(receiverIds.size <= MAX_RELAY_BATCH) {
            "Relay batch size exceeds limit: ${receiverIds.size} > $MAX_RELAY_BATCH"
        }

        return try {
            val payload = objectMapper.writeValueAsString(RelayEnvelope(receiverIds, message))
            stringRedisTemplate.convertAndSend(BROADCAST_CHANNEL, payload)
            true
        } catch (e: Exception) {
            log.error("Failed to publish relay batch: receiverIds={}", receiverIds, e)
            false
        }
    }

    companion object {
        const val BROADCAST_CHANNEL = "chat:relay"
        const val MAX_RELAY_BATCH = 1000
    }
}
