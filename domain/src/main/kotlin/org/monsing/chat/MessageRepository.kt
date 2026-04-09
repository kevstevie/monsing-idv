package org.monsing.chat

import kotlin.reflect.KProperty
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.insert
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lt
import org.springframework.stereotype.Component

private const val DEFAULT_SIZE = 10
private const val MAXIMUM_ID = "99999999999999999999"

@Component
class MessageRepository(
    private val mongoTemplate: MongoTemplate
) {

    fun save(message: Message): Message {
        return mongoTemplate.insert(message)
    }

    fun saveAll(messages: List<Message>) {
        mongoTemplate.insert<Message>(messages)
    }

    fun findById(messageId: String): Message? {
        return mongoTemplate.findById(messageId, Message::class.java)
    }

    fun findByChatId(chatId: String, lastId: String?, limit: Int?): List<Message> {
        val query = Query().addCriteria(
            (Message::id lt (lastId ?: MAXIMUM_ID))
                .andOperator(Message::chatId isEqualTo chatId)
        ).with(
            sortBy(Message::id, Sort.Direction.DESC)
        ).limit(limit ?: DEFAULT_SIZE)

        return mongoTemplate.find(
            query,
            Message::class.java
        )
    }

    fun sortBy(property: KProperty<*>, direction: Sort.Direction = Sort.Direction.ASC): Sort {
        return Sort.by(Sort.Order(direction, property.name))
    }

    fun findLastMessageByChatId(chatId: String): Message? {
        val query = Query().addCriteria(
            Message::chatId isEqualTo chatId
        ).with(
            sortBy(Message::id, Sort.Direction.DESC)
        ).limit(1)

        return mongoTemplate.findOne(
            query,
            Message::class.java
        )
    }
}
