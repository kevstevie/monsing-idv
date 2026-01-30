package org.monsing.worker

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.ChatStreamConstants
import org.monsing.chat.Message
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component

@Component
class ChatMessageConsumer(
    private val chatMessageRelay: ChatMessageRelay,
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : StreamListener<String, MapRecord<String, String, String>> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(record: MapRecord<String, String, String>) {
        try {
            val receiverId = record.value[ChatStreamConstants.FIELD_RECEIVER_ID]?.toLongOrNull()
            val messageJson = record.value[ChatStreamConstants.FIELD_MESSAGE]

            if (receiverId == null || messageJson == null) {
                log.warn("Invalid stream record: {}", record.id)
                acknowledge(record)
                return
            }

            val message = objectMapper.readValue(messageJson, Message::class.java)
            chatMessageRelay.relay(receiverId, message)
        } catch (e: JacksonException) {
            log.error("Failed to deserialize stream record {}: {}", record.id, e.message)
        } finally {
            acknowledge(record)
        }
    }

    private fun acknowledge(record: MapRecord<String, String, String>) {
        stringRedisTemplate.opsForStream<String, String>()
            .acknowledge(ChatStreamConstants.STREAM_KEY, ChatStreamConstants.CONSUMER_GROUP, record.id)
    }
}

