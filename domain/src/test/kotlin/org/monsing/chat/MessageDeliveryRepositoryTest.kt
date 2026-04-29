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

    private fun persist(
        messageId: String = "msg-1",
        receiverId: Long = 1L,
        status: MessageStatus = MessageStatus.FAILED,
        retryCount: Int = 0,
        updatedAt: LocalDateTime = LocalDateTime.now()
    ): Long {
        val saved = repository.save(
            MessageDelivery(
                messageId = messageId,
                receiverId = receiverId,
                status = status,
                retryCount = retryCount,
                updatedAt = updatedAt
            )
        )
        entityManager.flush()
        entityManager.clear()
        return saved.id!!
    }

    private fun reload(id: Long): MessageDelivery {
        entityManager.clear()
        return repository.findById(id).get()
    }

    @Nested
    @DisplayName("markNotified - FAILED 가드 (AckHandler race 방어)")
    inner class MarkNotifiedGuard {

        @Test
        fun `FAILED 상태만 NOTIFIED 로 전이`() {
            val id = persist(status = MessageStatus.FAILED)

            val affected = repository.markNotified(listOf(id))

            affected shouldBe 1
            reload(id).status shouldBe MessageStatus.NOTIFIED
        }

        @Test
        fun `COMPLETE 로 이미 전이된 row 는 덮어쓰지 않음`() {
            val id = persist(status = MessageStatus.COMPLETE)

            val affected = repository.markNotified(listOf(id))

            affected shouldBe 0
            reload(id).status shouldBe MessageStatus.COMPLETE
        }

        @Test
        fun `PENDING RELAY_PENDING NOTIFIED DEAD_LETTERED 도 덮어쓰지 않음`() {
            val pending = persist(messageId = "p", status = MessageStatus.PENDING)
            val relay = persist(messageId = "r", status = MessageStatus.RELAY_PENDING)
            val notified = persist(messageId = "n", status = MessageStatus.NOTIFIED)
            val dead = persist(messageId = "d", status = MessageStatus.DEAD_LETTERED)

            val affected = repository.markNotified(listOf(pending, relay, notified, dead))

            affected shouldBe 0
            reload(pending).status shouldBe MessageStatus.PENDING
            reload(relay).status shouldBe MessageStatus.RELAY_PENDING
            reload(notified).status shouldBe MessageStatus.NOTIFIED
            reload(dead).status shouldBe MessageStatus.DEAD_LETTERED
        }

        @Test
        fun `FAILED 와 COMPLETE 섞여 있으면 FAILED 만 전이`() {
            val failed = persist(messageId = "f", status = MessageStatus.FAILED)
            val completed = persist(messageId = "c", status = MessageStatus.COMPLETE)

            val affected = repository.markNotified(listOf(failed, completed))

            affected shouldBe 1
            reload(failed).status shouldBe MessageStatus.NOTIFIED
            reload(completed).status shouldBe MessageStatus.COMPLETE
        }
    }

    @Nested
    @DisplayName("markDeadLettered - FAILED 가드")
    inner class MarkDeadLetteredGuard {

        @Test
        fun `FAILED 만 DEAD_LETTERED 로 전이하고 retryCount 는 보존`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 3)

            repository.markDeadLettered(listOf(id))

            val loaded = reload(id)
            loaded.status shouldBe MessageStatus.DEAD_LETTERED
            loaded.retryCount shouldBe 3
        }

        @Test
        fun `COMPLETE 된 row 는 덮어쓰지 않음 (race 방어)`() {
            val id = persist(status = MessageStatus.COMPLETE)

            val affected = repository.markDeadLettered(listOf(id))

            affected shouldBe 0
            reload(id).status shouldBe MessageStatus.COMPLETE
        }

        @Test
        fun `NOTIFIED 된 row 는 DEAD_LETTERED 로 강등되지 않음`() {
            val id = persist(status = MessageStatus.NOTIFIED)

            repository.markDeadLettered(listOf(id))

            reload(id).status shouldBe MessageStatus.NOTIFIED
        }
    }

    @Nested
    @DisplayName("incrementRetry")
    inner class IncrementRetry {

        @Test
        fun `FAILED 의 retryCount 만 증가하고 status 는 유지`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 0)

            repository.incrementRetry(listOf(id))

            val loaded = reload(id)
            loaded.retryCount shouldBe 1
            loaded.status shouldBe MessageStatus.FAILED
        }

        @Test
        fun `반복 호출 시 누적 증가`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 0)

            repository.incrementRetry(listOf(id))
            repository.incrementRetry(listOf(id))
            repository.incrementRetry(listOf(id))

            reload(id).retryCount shouldBe 3
        }

        @Test
        fun `COMPLETE 된 row 의 retryCount 는 증가하지 않음 (race 방어)`() {
            val id = persist(status = MessageStatus.COMPLETE, retryCount = 0)

            val affected = repository.incrementRetry(listOf(id))

            affected shouldBe 0
            reload(id).retryCount shouldBe 0
        }

        @Test
        fun `FAILED 복수 건 일괄 증가, COMPLETE 는 제외`() {
            val a = persist(messageId = "a", status = MessageStatus.FAILED, retryCount = 0)
            val b = persist(messageId = "b", status = MessageStatus.FAILED, retryCount = 1)
            val c = persist(messageId = "c", status = MessageStatus.COMPLETE, retryCount = 0)

            val affected = repository.incrementRetry(listOf(a, b, c))

            affected shouldBe 2
            reload(a).retryCount shouldBe 1
            reload(b).retryCount shouldBe 2
            reload(c).retryCount shouldBe 0
        }
    }

    @Nested
    @DisplayName("updateStatus - 상태 무관 강제 전이")
    inner class UpdateStatus {

        @Test
        fun `messageId receiverId 매칭 row 만 상태 변경`() {
            val a = persist(messageId = "msg-1", receiverId = 10L, status = MessageStatus.PENDING)
            val b = persist(messageId = "msg-1", receiverId = 20L, status = MessageStatus.PENDING)

            val affected = repository.updateStatus("msg-1", 10L, MessageStatus.COMPLETE)

            affected shouldBe 1
            reload(a).status shouldBe MessageStatus.COMPLETE
            reload(b).status shouldBe MessageStatus.PENDING
        }

        @Test
        fun `FAILED 상태도 updateStatus 로 COMPLETE 전이 가능 (AckHandler 뒤늦은 ACK 경로)`() {
            val id = persist(messageId = "msg-1", receiverId = 10L, status = MessageStatus.FAILED)

            val affected = repository.updateStatus("msg-1", 10L, MessageStatus.COMPLETE)

            affected shouldBe 1
            reload(id).status shouldBe MessageStatus.COMPLETE
        }
    }

    @Nested
    @DisplayName("findAllByStatusOrderByUpdatedAtAsc - FCM scan 쿼리")
    inner class FindByStatusAsc {

        @Test
        fun `FAILED 만 반환하고 updatedAt ASC 정렬`() {
            val now = LocalDateTime.now()
            persist(messageId = "old", status = MessageStatus.FAILED, updatedAt = now.minusMinutes(10))
            persist(messageId = "mid", status = MessageStatus.FAILED, updatedAt = now.minusMinutes(5))
            persist(messageId = "fresh", status = MessageStatus.FAILED, updatedAt = now.minusMinutes(1))
            persist(messageId = "done", status = MessageStatus.COMPLETE, updatedAt = now.minusMinutes(20))
            persist(messageId = "dead", status = MessageStatus.DEAD_LETTERED, updatedAt = now.minusMinutes(15))

            val result = repository.findAllByStatusOrderByUpdatedAtAsc(
                MessageStatus.FAILED, Pageable.ofSize(10)
            )

            result.map { it.messageId } shouldContainExactly listOf("old", "mid", "fresh")
        }

        @Test
        fun `pageSize 만큼 상한 적용`() {
            val now = LocalDateTime.now()
            repeat(5) { idx ->
                persist(
                    messageId = "m-$idx",
                    receiverId = idx.toLong(),
                    status = MessageStatus.FAILED,
                    updatedAt = now.minusSeconds(idx.toLong())
                )
            }

            val result = repository.findAllByStatusOrderByUpdatedAtAsc(
                MessageStatus.FAILED, Pageable.ofSize(3)
            )

            result shouldHaveSize 3
        }
    }

    @Nested
    @DisplayName("통합 시나리오 - 실제 FCM 재시도 흐름")
    inner class FcmRetryFlow {

        @Test
        fun `3회 발송 실패 시뮬레이션 - retry 2회 후 DEAD_LETTERED`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 0)

            // Tick 1: 발송 실패 → retryCount 1
            repository.incrementRetry(listOf(id))
            reload(id).retryCount shouldBe 1

            // Tick 2: 발송 실패 → retryCount 2
            repository.incrementRetry(listOf(id))
            reload(id).retryCount shouldBe 2

            // Tick 3: 발송 실패 + retryCount+1 >= MAX_ATTEMPTS(3) → DEAD_LETTERED
            repository.markDeadLettered(listOf(id))

            val final = reload(id)
            final.status shouldBe MessageStatus.DEAD_LETTERED
            final.retryCount shouldBe 2
        }

        @Test
        fun `race 시나리오 - FCM I O 중 ACK 수신하여 COMPLETE 된 row 는 markNotified 무시`() {
            val id = persist(status = MessageStatus.FAILED)

            // FCM 스케줄러가 id 선점 후 전송 중 사용자 ACK 도착 → AckHandler 가 COMPLETE 로 전이
            val delivery = reload(id)
            repository.updateStatus(delivery.messageId, delivery.receiverId, MessageStatus.COMPLETE)
            // FCM 스케줄러가 전송 완료 후 markNotified 호출 → guard 덕분에 0 rows
            val affected = repository.markNotified(listOf(id))

            affected shouldBe 0
            reload(id).status shouldBe MessageStatus.COMPLETE
        }

        @Test
        fun `race 시나리오 - COMPLETE 된 row 에 incrementRetry 호출 시 retryCount 보존`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 1)

            val delivery = reload(id)
            repository.updateStatus(delivery.messageId, delivery.receiverId, MessageStatus.COMPLETE)

            repository.incrementRetry(listOf(id))

            val loaded = reload(id)
            loaded.status shouldBe MessageStatus.COMPLETE
            loaded.retryCount shouldBe 1
        }

        @Test
        fun `race 시나리오 - COMPLETE 된 row 에 markDeadLettered 호출 시 상태 보존`() {
            val id = persist(status = MessageStatus.FAILED, retryCount = 3)

            val delivery = reload(id)
            repository.updateStatus(delivery.messageId, delivery.receiverId, MessageStatus.COMPLETE)

            val affected = repository.markDeadLettered(listOf(id))

            affected shouldBe 0
            reload(id).status shouldBe MessageStatus.COMPLETE
        }
    }
}
