package org.monsing.config

import java.time.Duration
import org.monsing.chat.ChatStreamConstants
import org.monsing.worker.ChatMessageConsumer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.stream.StreamMessageListenerContainer

@Configuration
class RedisStreamConfig(
    private val connectionFactory: RedisConnectionFactory,
    private val chatMessageConsumer: ChatMessageConsumer
) {

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun streamMessageListenerContainer(): StreamMessageListenerContainer<String, MapRecord<String, String, String>> {
        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofSeconds(1))
            .build()

        val container = StreamMessageListenerContainer.create(connectionFactory, options)

        initConsumerGroup()

        container.receive(
            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
            StreamOffset.create(ChatStreamConstants.STREAM_KEY, ReadOffset.lastConsumed()),
            chatMessageConsumer
        )

        return container
    }

    private fun initConsumerGroup() {
        try {
            connectionFactory.connection
                .streamCommands()
                .xGroupCreate(
                    ChatStreamConstants.STREAM_KEY.toByteArray(),
                    ChatStreamConstants.CONSUMER_GROUP,
                    ReadOffset.from("0"),
                    true
                )
        } catch (_: RedisSystemException) {
            // consumer group already exists
        }
    }

    companion object {
        private const val CONSUMER_GROUP = ChatStreamConstants.CONSUMER_GROUP
        private const val CONSUMER_NAME = "chat-worker"
    }
}

