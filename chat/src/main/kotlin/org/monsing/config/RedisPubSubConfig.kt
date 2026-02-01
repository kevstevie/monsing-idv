package org.monsing.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisPubSubConfig {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): RedisMessageListenerContainer {
        return RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
        }
    }
}
