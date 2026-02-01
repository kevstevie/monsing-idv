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

    fun publishToUser(receiverId: Long, message: Message): Boolean {
        val channel = getUserChannel(receiverId)
        val payload = objectMapper.writeValueAsString(message)

        return try {
            val receivedCount = stringRedisTemplate.convertAndSend(channel, payload)
            log.debug("Published message to channel: {}, receivedCount: {}", channel, receivedCount)
            (receivedCount ?: 0) > 0
        } catch (e: Exception) {
            log.error("Failed to publish message to channel: {}", channel, e)
            false
        }
    }

    private fun getUserChannel(memberId: Long): String = "chat:user:$memberId"
}
