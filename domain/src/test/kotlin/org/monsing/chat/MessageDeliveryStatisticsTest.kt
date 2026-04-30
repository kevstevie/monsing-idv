package org.monsing.chat

import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.monsing.TestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration

@DataJpaTest
@ContextConfiguration(classes = [TestContext::class])
@EnableJpaRepositories(basePackageClasses = [MessageDeliveryRepository::class])
@EntityScan(basePackageClasses = [MessageDelivery::class])
class MessageDeliveryStatisticsTest(
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

    private fun persistPending(messageId: String = "msg-1", receiverId: Long = 1L): Long {
        val saved = repository.save(MessageDelivery(messageId = messageId, receiverId = receiverId))
        entityManager.flush()
        entityManager.clear()
        return saved.id!!
    }

    private fun reload(id: Long): MessageDelivery {
        entityManager.clear()
        return repository.findById(id).get()
    }

    @Nested
    @DisplayName("@Version - 정상 update / silent skip")
    inner class VersionLifecycle {

        @Test
        fun `정상 update 시 version 증가`() {
            val id = persistPending()
            val versionBefore = reload(id).version

            val d = repository.findById(id).get()
            d.transitionTo(MessageStatus.COMPLETE)
            entityManager.flush()

            reload(id).version shouldBe versionBefore + 1
        }

        @Test
        fun `silent skip 시 version 보존 (update 쿼리 미발행)`() {
            val id = persistPending()
            run {
                val d = repository.findById(id).get()
                d.transitionTo(MessageStatus.COMPLETE)
                entityManager.flush()
                entityManager.clear()
            }
            val versionAfterFirstUpdate = reload(id).version

            val stats = statistics()
            val terminal = repository.findById(id).get()
            terminal.transitionTo(MessageStatus.FAILED)
            entityManager.flush()

            stats.entityUpdateCount shouldBe 0
            reload(id).version shouldBe versionAfterFirstUpdate
            reload(id).status shouldBe MessageStatus.COMPLETE
        }
    }

    @Nested
    @DisplayName("batch update - JPA dirty checking")
    inner class BatchUpdate {

        @Test
        fun `여러 entity transitionTo 후 한 번의 flush 로 일괄 update`() {
            val ids = (1L..10L).map { persistPending(messageId = "m-$it", receiverId = it) }
            val stats = statistics()

            val deliveries = ids.map { repository.findById(it).get() }
            deliveries.forEach { it.transitionTo(MessageStatus.FAILED) }
            entityManager.flush()

            stats.entityUpdateCount shouldBe 10
            ids.forEach { id ->
                reload(id).status shouldBe MessageStatus.FAILED
            }
        }

        @Test
        fun `섞인 batch - silent skip 된 entity 는 update 카운트 제외`() {
            val pending = persistPending(messageId = "p", receiverId = 1L)
            val completed = persistPending(messageId = "c", receiverId = 2L)
            run {
                val d = repository.findById(completed).get()
                d.transitionTo(MessageStatus.COMPLETE)
                entityManager.flush()
                entityManager.clear()
            }
            val stats = statistics()

            val a = repository.findById(pending).get()
            a.transitionTo(MessageStatus.FAILED)
            val b = repository.findById(completed).get()
            b.transitionTo(MessageStatus.FAILED)
            entityManager.flush()

            stats.entityUpdateCount shouldBe 1
            reload(pending).status shouldBe MessageStatus.FAILED
            reload(completed).status shouldBe MessageStatus.COMPLETE
        }
    }
}
