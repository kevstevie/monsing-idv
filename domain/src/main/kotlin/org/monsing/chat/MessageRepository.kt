package org.monsing.chat

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private const val DEFAULT_SIZE = 10
private const val MAXIMUM_ID = "ffffffff-ffff-ffff-ffff-ffffffffffff"

interface JpaMessageRepository : JpaRepository<Message, String> {

    fun findTopByChatIdOrderByIdDesc(chatId: Long): Message?

    fun findByChatIdAndIdLessThanOrderByIdDesc(
        chatId: Long,
        id: String,
        pageable: Pageable
    ): List<Message>
}

@Component
class MessageRepository(
    private val jpaMessageRepository: JpaMessageRepository
) {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    fun save(message: Message): Message {
        entityManager.persist(message)
        return message
    }

    @Transactional
    fun saveAll(messages: List<Message>) {
        messages.forEach { entityManager.persist(it) }
    }

    fun findById(messageId: String): Message? {
        return jpaMessageRepository.findByIdOrNull(messageId)
    }

    fun findByChatId(chatId: Long, lastId: String?, limit: Int?): List<Message> {
        return jpaMessageRepository.findByChatIdAndIdLessThanOrderByIdDesc(
            chatId,
            lastId ?: MAXIMUM_ID,
            Pageable.ofSize(limit ?: DEFAULT_SIZE)
        )
    }

    fun findLastMessageByChatId(chatId: Long): Message? {
        return jpaMessageRepository.findTopByChatIdOrderByIdDesc(chatId)
    }
}
