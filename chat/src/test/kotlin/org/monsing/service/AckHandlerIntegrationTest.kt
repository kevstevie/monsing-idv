package org.monsing.service

import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@DataJpaTest
@Import(AckHandler::class)
@EnableJpaRepositories(basePackageClasses = [MessageDeliveryRepository::class])
@EntityScan(basePackageClasses = [MessageDelivery::class])
class AckHandlerIntegrationTest(
    @Autowired private val ackHandler: AckHandler,
    @Autowired private val repository: MessageDeliveryRepository,
    @Autowired private val entityManager: TestEntityManager,
    @Autowired private val em: EntityManager
) {

    private fun statistics(): Statistics =
        em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics.apply { clear() }

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `handleAck - 트랜잭션 안에서 PENDING 을 COMPLETE 로 전이 후 flush 시 update 1건`() {
        val saved = repository.save(MessageDelivery(messageId = "msg-1", receiverId = 10L))
        entityManager.flush()
        entityManager.clear()
        val stats = statistics()

        ackHandler.handleAck(memberId = 10L, messageId = "msg-1")
        entityManager.flush()

        stats.entityUpdateCount shouldBe 1
        entityManager.clear()
        repository.findById(saved.id!!).get().status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `handleAck - 이미 COMPLETE 인 row 호출 시 update 쿼리 미발행 (silent skip + dirty checking)`() {
        val saved = repository.save(MessageDelivery(messageId = "msg-1", receiverId = 10L))
        run {
            val d = repository.findById(saved.id!!).get()
            d.transitionTo(MessageStatus.COMPLETE)
            entityManager.flush()
            entityManager.clear()
        }
        val versionAfter = repository.findById(saved.id!!).get().version
        entityManager.clear()
        val stats = statistics()

        ackHandler.handleAck(memberId = 10L, messageId = "msg-1")
        entityManager.flush()

        stats.entityUpdateCount shouldBe 0
        entityManager.clear()
        repository.findById(saved.id!!).get().version shouldBe versionAfter
    }

    @Test
    fun `handleAck - FAILED 인 row 호출 시 silent skip (status FAILED 보존)`() {
        val saved = repository.save(MessageDelivery(messageId = "msg-1", receiverId = 10L))
        run {
            val d = repository.findById(saved.id!!).get()
            d.transitionTo(MessageStatus.FAILED)
            entityManager.flush()
            entityManager.clear()
        }
        val stats = statistics()

        ackHandler.handleAck(memberId = 10L, messageId = "msg-1")
        entityManager.flush()

        stats.entityUpdateCount shouldBe 0
        entityManager.clear()
        repository.findById(saved.id!!).get().status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `handleAck - 매칭 row 없으면 update 쿼리 없음`() {
        val stats = statistics()

        ackHandler.handleAck(memberId = 999L, messageId = "missing")
        entityManager.flush()

        stats.entityUpdateCount shouldBe 0
    }
}
