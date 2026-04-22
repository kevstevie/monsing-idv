package org.monsing.chat

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

interface JpaMessageReceivedRepository : JpaRepository<MessageReceived, String>

@Component
class MessageReceivedRepository(
    private val jpaMessageReceivedRepository: JpaMessageReceivedRepository
) {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    fun save(received: MessageReceived): MessageReceived {
        entityManager.persist(received)
        return received
    }

    fun findById(clientMessageId: String): MessageReceived? {
        return jpaMessageReceivedRepository.findByIdOrNull(clientMessageId)
    }
}
