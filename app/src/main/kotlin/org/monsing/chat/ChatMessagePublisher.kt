package org.monsing.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class ChatMessagePublisher(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    fun publish(receiverId: Long, message: Message) {
        val record = MapRecord.create(
            ChatStreamConstants.STREAM_KEY,
            mapOf(
                ChatStreamConstants.FIELD_RECEIVER_ID to receiverId.toString(),
                ChatStreamConstants.FIELD_MESSAGE to objectMapper.writeValueAsString(message)
            )
        )
        stringRedisTemplate.opsForStream<String, String>().add(record)
    }
}
