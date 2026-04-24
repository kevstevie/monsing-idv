package org.monsing.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.service.MessageDto
import org.monsing.service.WebSocketFrameType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InboundFrameParser(
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun parse(payload: String): InboundFrame? {
        val node = objectMapper.readTree(payload)
        val rawType = node.get("type")?.asText()
        val frameType = rawType?.let { WebSocketFrameType.fromValue(it) } ?: WebSocketFrameType.CHAT

        return when (frameType) {
            WebSocketFrameType.CHAT -> InboundFrame.Chat(objectMapper.treeToValue(node, MessageDto::class.java))
            WebSocketFrameType.ACK -> {
                val messageId = node.get("messageId")?.asText()
                if (messageId == null) {
                    log.warn("ACK frame missing messageId")
                    null
                } else {
                    InboundFrame.Ack(messageId)
                }
            }
            WebSocketFrameType.SEND_ACK -> {
                log.warn("Received server-only frame type '{}' from client", rawType)
                null
            }
        }
    }
}
