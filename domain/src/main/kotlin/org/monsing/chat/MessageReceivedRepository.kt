package org.monsing.chat

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Component

@Component
class MessageReceivedRepository(
    private val mongoTemplate: MongoTemplate
) {

    fun save(received: MessageReceived): MessageReceived {
        return mongoTemplate.insert(received)
    }

    fun findById(clientMessageId: String): MessageReceived? {
        return mongoTemplate.findById<MessageReceived>(clientMessageId)
    }
}
