package org.monsing.worker

import org.monsing.chat.Message
import org.monsing.chat.session.GlobalServerIdStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Component
class ChatMessageRelay(
    private val globalServerIdStorage: GlobalServerIdStorage,
    private val restTemplate: RestTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun relay(receiverId: Long, message: Message) {
        val serverIds = globalServerIdStorage.getServerId(receiverId)

        serverIds.takeIf { it.isNotEmpty() }
            ?.forEach { serverId ->
                sendToServer(serverId, receiverId, message)
            } ?: handleOfflineUser(receiverId, message)
    }

    private fun sendToServer(serverId: String, receiverId: Long, message: Message) {
        try {
            restTemplate.postForEntity(
                "http://$serverId/relay?receiverId=$receiverId",
                message,
                Void::class.java
            )
        } catch (e: RestClientException) {
            log.error("Failed to relay message to server {}: {}", serverId, e.message)
        }
    }

    private fun handleOfflineUser(receiverId: Long, message: Message) {
        log.info("User {} is offline, message {} pending for push notification", receiverId, message.id)
    }
}

