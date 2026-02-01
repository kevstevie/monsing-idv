package org.monsing.service.relay

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import org.monsing.chat.Message
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

    private val activeListeners = ConcurrentHashMap<Long, MessageListener>()

    fun subscribe(memberId: Long) {
        if (activeListeners.containsKey(memberId)) {
            log.debug("Already subscribed to user channel: {}", memberId)
            return
        }

        val channel = getUserChannel(memberId)
        val listener = createMessageListener(memberId)

        try {
            listenerContainer.addMessageListener(listener, ChannelTopic(channel))
            activeListeners[memberId] = listener
            log.info("Subscribed to channel: {}", channel)
        } catch (e: Exception) {
            log.error("Failed to subscribe to channel: {}, error: {}", channel, e.message, e)
            throw e
        }
    }

    fun unsubscribe(memberId: Long) {
        val listener = activeListeners.remove(memberId) ?: run {
            log.debug("No active subscription for user: {}", memberId)
            return
        }

        val channel = getUserChannel(memberId)

        try {
            listenerContainer.removeMessageListener(listener, ChannelTopic(channel))
            log.info("Unsubscribed from channel: {}", channel)
        } catch (e: Exception) {
            log.error("Failed to unsubscribe from channel: {}, error: {}", channel, e.message, e)
        }
    }

    private fun createMessageListener(receiverId: Long): MessageListener {
        return MessageListener { message, _ ->
            try {
                val messageBody = String(message.body)
                val msg = objectMapper.readValue(messageBody, Message::class.java)

                log.debug("Received message for user {}, messageId: {}", receiverId, msg.id)
                chatMessageHandler.relayMessage(receiverId, msg)
            } catch (e: Exception) {
                log.error("Failed to process message for user {}, error: {}", receiverId, e.message, e)
            }
        }
    }

    private fun getUserChannel(memberId: Long): String = "chat:user:$memberId"
}
