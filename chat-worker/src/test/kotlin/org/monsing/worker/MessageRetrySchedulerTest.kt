package org.monsing.worker

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus
import org.springframework.data.domain.Pageable

class MessageRetrySchedulerTest {

    private lateinit var deliveryRepo: MessageDeliveryRepository
    private lateinit var scheduler: MessageRetryScheduler

    @BeforeEach
    fun setUp() {
        deliveryRepo = mockk(relaxed = true)
        scheduler = MessageRetryScheduler(deliveryRepo)
    }

    @Test
    fun `expireUndelivered - PENDING 은 FAILED 로 transitionTo`() {
        val d = pendingDelivery(messageId = "m-1", receiverId = 2L)
        givenExpired(listOf(d))

        scheduler.expireUndelivered()

        d.status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `expireUndelivered - RELAY_PENDING 도 FAILED 로 transitionTo`() {
        val d = pendingDelivery(messageId = "m-1", receiverId = 2L).apply {
            transitionTo(MessageStatus.RELAY_PENDING)
        }
        givenExpired(listOf(d))

        scheduler.expireUndelivered()

        d.status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `expireUndelivered - 만료 대상 없으면 호출 없음`() {
        givenExpired(emptyList())

        scheduler.expireUndelivered()
    }

    @Test
    fun `expireUndelivered - 여러 entity 모두 transitionTo`() {
        val d1 = pendingDelivery(messageId = "m-1", receiverId = 2L)
        val d2 = pendingDelivery(messageId = "m-2", receiverId = 3L).apply {
            transitionTo(MessageStatus.RELAY_PENDING)
        }
        givenExpired(listOf(d1, d2))

        scheduler.expireUndelivered()

        d1.status shouldBe MessageStatus.FAILED
        d2.status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `expireUndelivered - cutoff 는 30 초 전 시점으로 호출`() {
        givenExpired(emptyList())
        val cutoffSlot = slot<LocalDateTime>()
        every {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(any(), capture(cutoffSlot), any())
        } returns emptyList()

        val before = LocalDateTime.now().minusSeconds(31)
        scheduler.expireUndelivered()
        val after = LocalDateTime.now().minusSeconds(29)

        cutoffSlot.captured.isAfter(before) shouldBe true
        cutoffSlot.captured.isBefore(after) shouldBe true
    }

    @Test
    fun `expireUndelivered - statuses 는 PENDING + RELAY_PENDING 만`() {
        val statusSlot = slot<Collection<MessageStatus>>()
        every {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(capture(statusSlot), any(), any())
        } returns emptyList()

        scheduler.expireUndelivered()

        statusSlot.captured.toSet() shouldBe setOf(MessageStatus.PENDING, MessageStatus.RELAY_PENDING)
    }

    @Test
    fun `expireUndelivered - Pageable size 는 BATCH_LIMIT 100`() {
        val pageableSlot = slot<Pageable>()
        every {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(any(), any(), capture(pageableSlot))
        } returns emptyList()

        scheduler.expireUndelivered()

        pageableSlot.captured.pageSize shouldBe 100
    }

    @Test
    fun `expireUndelivered - 비정상적으로 COMPLETE 가 list 에 들어와도 silent skip`() {
        val complete = pendingDelivery(messageId = "x", receiverId = 9L).apply {
            transitionTo(MessageStatus.COMPLETE)
        }
        givenExpired(listOf(complete))

        scheduler.expireUndelivered()

        complete.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `expireUndelivered - 빈 리스트면 스캔만 하고 update 시도 없음`() {
        givenExpired(emptyList())

        scheduler.expireUndelivered()

        verify(exactly = 1) {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(any(), any(), any())
        }
    }

    private fun givenExpired(list: List<MessageDelivery>) {
        every {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(any(), any(), any())
        } returns list
    }

    private fun pendingDelivery(messageId: String, receiverId: Long) =
        MessageDelivery(messageId = messageId, receiverId = receiverId)
}
