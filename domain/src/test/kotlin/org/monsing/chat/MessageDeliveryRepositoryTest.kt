package org.monsing.chat

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.monsing.TestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration

@DataJpaTest
@ContextConfiguration(classes = [TestContext::class])
@EnableJpaRepositories(basePackageClasses = [MessageDeliveryRepository::class])
@EntityScan(basePackageClasses = [MessageDelivery::class])
class MessageDeliveryRepositoryTest(
    @Autowired private val repository: MessageDeliveryRepository,
    @Autowired private val entityManager: TestEntityManager
) {

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    private fun persistPending(
        messageId: String = "msg-1",
        receiverId: Long = 1L,
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): Long {
        val saved = repository.save(
            MessageDelivery(
                messageId = messageId,
                receiverId = receiverId,
                updatedAt = updatedAt
            )
        )
        entityManager.flush()
        entityManager.clear()
        return saved.id!!
    }

    private fun persistFailed(
        messageId: String = "msg-1",
        receiverId: Long = 1L,
        retryCount: Int = 0,
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): Long {
        val d = MessageDelivery(
            messageId = messageId,
            receiverId = receiverId,
            updatedAt = updatedAt
        )
        d.transitionTo(MessageStatus.FAILED)
        repeat(retryCount) { d.incrementRetry() }
        val saved = repository.save(d)
        entityManager.flush()
        entityManager.clear()
        return saved.id!!
    }

    private fun reload(id: Long): MessageDelivery {
        entityManager.clear()
        return repository.findById(id).get()
    }

    @Nested
    @DisplayName("findByMessageIdAndReceiverId")
    inner class FindByMessageReceiver {

        @Test
        fun `messageId receiverId 매칭 row 반환`() {
            persistPending(messageId = "msg-1", receiverId = 10L)
            persistPending(messageId = "msg-1", receiverId = 20L)

            val found = repository.findByMessageIdAndReceiverId("msg-1", 10L)

            (found != null) shouldBe true
            found?.messageId shouldBe "msg-1"
            found?.receiverId shouldBe 10L
            found?.status shouldBe MessageStatus.PENDING
        }

        @Test
        fun `같은 messageId 의 다른 receiverId 와 분리 반환`() {
            persistPending(messageId = "msg-1", receiverId = 10L)
            persistPending(messageId = "msg-1", receiverId = 20L)

            val a = repository.findByMessageIdAndReceiverId("msg-1", 10L)
            val b = repository.findByMessageIdAndReceiverId("msg-1", 20L)

            a?.receiverId shouldBe 10L
            b?.receiverId shouldBe 20L
            (a?.id == b?.id) shouldBe false
        }

        @Test
        fun `매칭 없으면 null`() {
            val found = repository.findByMessageIdAndReceiverId("none", 999L)

            found shouldBe null
        }
    }

    @Nested
    @DisplayName("findAllByMessageIdAndReceiverIdIn")
    inner class FindAllByMessageReceiverIn {

        @Test
        fun `messageId 와 receiverIds In 으로 매칭 rows 반환`() {
            persistPending(messageId = "msg-1", receiverId = 10L)
            persistPending(messageId = "msg-1", receiverId = 20L)
            persistPending(messageId = "msg-1", receiverId = 30L)

            val found = repository.findAllByMessageIdAndReceiverIdIn("msg-1", listOf(10L, 30L))

            found.size shouldBe 2
            found.map { it.receiverId }.toSet() shouldBe setOf(10L, 30L)
        }

        @Test
        fun `다른 messageId 는 매칭에서 제외`() {
            persistPending(messageId = "msg-1", receiverId = 10L)
            persistPending(messageId = "msg-2", receiverId = 10L)

            val found = repository.findAllByMessageIdAndReceiverIdIn("msg-1", listOf(10L))

            found.size shouldBe 1
            found[0].messageId shouldBe "msg-1"
        }

        @Test
        fun `매칭 없으면 빈 리스트`() {
            val found = repository.findAllByMessageIdAndReceiverIdIn("none", listOf(1L, 2L))

            found.isEmpty() shouldBe true
        }

        @Test
        fun `빈 receiverIds 면 빈 리스트`() {
            persistPending(messageId = "msg-1", receiverId = 10L)

            val found = repository.findAllByMessageIdAndReceiverIdIn("msg-1", emptyList())

            found.isEmpty() shouldBe true
        }
    }

    @Nested
    @DisplayName("transitionTo - dirty checking flush")
    inner class TransitionToFlush {

        @Test
        fun `PENDING entity 의 transitionTo(COMPLETE) 가 flush 시 status update 발행`() {
            val id = persistPending()

            val d = repository.findById(id).get()
            d.transitionTo(MessageStatus.COMPLETE)
            entityManager.flush()

            reload(id).status shouldBe MessageStatus.COMPLETE
        }

        @Test
        fun `PENDING entity 의 transitionTo(RELAY_PENDING) 가 flush 시 반영`() {
            val id = persistPending()

            val d = repository.findById(id).get()
            d.transitionTo(MessageStatus.RELAY_PENDING)
            entityManager.flush()

            reload(id).status shouldBe MessageStatus.RELAY_PENDING
        }

        @Test
        fun `FAILED entity 의 transitionTo(NOTIFIED) 가 flush 시 반영`() {
            val id = persistFailed()

            val d = repository.findById(id).get()
            d.transitionTo(MessageStatus.NOTIFIED)
            entityManager.flush()

            reload(id).status shouldBe MessageStatus.NOTIFIED
        }

        @Test
        fun `incrementRetry 가 flush 시 retryCount update 발행`() {
            val id = persistFailed(retryCount = 1)

            val d = repository.findById(id).get()
            d.incrementRetry()
            entityManager.flush()

            reload(id).retryCount shouldBe 2
        }
    }

    @Nested
    @DisplayName("optimistic lock - @Version 충돌")
    inner class OptimisticLock {

        @Test
        fun `stale entity 로 saveAndFlush 시 OptimisticLockingFailureException`() {
            val id = persistPending()

            val stale = repository.findById(id).get()
            entityManager.detach(stale)

            val fresh = repository.findById(id).get()
            fresh.transitionTo(MessageStatus.COMPLETE)
            repository.saveAndFlush(fresh)
            entityManager.clear()

            stale.transitionTo(MessageStatus.FAILED)
            val ex = runCatching {
                repository.saveAndFlush(stale)
            }.exceptionOrNull()

            (ex is OptimisticLockingFailureException) shouldBe true
            reload(id).status shouldBe MessageStatus.COMPLETE
        }
    }

    @Nested
    @DisplayName("findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc - retry scan")
    inner class FindByExpiredScan {

        @Test
        fun `PENDING RELAY_PENDING 만 cutoff 이전 row 반환 ASC`() {
            val now = LocalDateTime.now()
            persistPending(messageId = "old", updatedAt = now.minusMinutes(10))
            persistPending(messageId = "fresh", updatedAt = now.minusSeconds(1))
            persistFailed(messageId = "fail", updatedAt = now.minusMinutes(20))

            val result = repository.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(
                listOf(MessageStatus.PENDING, MessageStatus.RELAY_PENDING),
                now.minusMinutes(5),
                Pageable.ofSize(10)
            )

            result.map { it.messageId } shouldContainExactly listOf("old")
        }
    }

    @Nested
    @DisplayName("findAllByStatusOrderByUpdatedAtAsc - FCM scan")
    inner class FindByStatusAsc {

        @Test
        fun `FAILED 만 반환하고 updatedAt ASC 정렬`() {
            val now = LocalDateTime.now()
            persistFailed(messageId = "old", updatedAt = now.minusMinutes(10))
            persistFailed(messageId = "mid", updatedAt = now.minusMinutes(5))
            persistFailed(messageId = "fresh", updatedAt = now.minusMinutes(1))
            persistPending(messageId = "p", updatedAt = now.minusMinutes(20))

            val result = repository.findAllByStatusOrderByUpdatedAtAsc(
                MessageStatus.FAILED, Pageable.ofSize(10)
            )

            result.map { it.messageId } shouldContainExactly listOf("old", "mid", "fresh")
        }

        @Test
        fun `pageSize 만큼 상한 적용`() {
            val now = LocalDateTime.now()
            repeat(5) { idx ->
                persistFailed(
                    messageId = "m-$idx",
                    receiverId = idx.toLong(),
                    updatedAt = now.minusSeconds(idx.toLong())
                )
            }

            val result = repository.findAllByStatusOrderByUpdatedAtAsc(
                MessageStatus.FAILED, Pageable.ofSize(3)
            )

            result shouldHaveSize 3
        }
    }
}
