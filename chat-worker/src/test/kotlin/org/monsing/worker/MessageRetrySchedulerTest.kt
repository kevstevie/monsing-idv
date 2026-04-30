package org.monsing.worker

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus

class MessageRetrySchedulerTest {

    private lateinit var deliveryRepo: MessageDeliveryRepository
    private lateinit var scheduler: MessageRetryScheduler

    @BeforeEach
    fun setUp() {
        deliveryRepo = mockk(relaxed = true)
        scheduler = MessageRetryScheduler(deliveryRepo)
    }

    @Test
    fun `expireUndelivered - PENDING이 timeout 지나면 markFailed`() {
        givenExpired(listOf(expiredDelivery(id = 100L, status = MessageStatus.PENDING)))

        scheduler.expireUndelivered()

        verify { deliveryRepo.markFailed(listOf(100L)) }
    }

    @Test
    fun `expireUndelivered - RELAY_PENDING도 동일하게 markFailed`() {
        givenExpired(listOf(expiredDelivery(id = 200L, status = MessageStatus.RELAY_PENDING)))

        scheduler.expireUndelivered()

        verify { deliveryRepo.markFailed(listOf(200L)) }
    }

    @Test
    fun `expireUndelivered - 만료 대상 없으면 markFailed 호출 안 함`() {
        givenExpired(emptyList())

        scheduler.expireUndelivered()

        verify(exactly = 0) { deliveryRepo.markFailed(any()) }
    }

    @Test
    fun `expireUndelivered - 여러 delivery는 하나의 markFailed 호출로 처리`() {
        val d1 = expiredDelivery(id = 100L, status = MessageStatus.PENDING)
        val d2 = expiredDelivery(id = 200L, status = MessageStatus.RELAY_PENDING)
        givenExpired(listOf(d1, d2))

        scheduler.expireUndelivered()

        verify(exactly = 1) { deliveryRepo.markFailed(listOf(100L, 200L)) }
    }

    private fun givenExpired(list: List<MessageDelivery>) {
        every {
            deliveryRepo.findAllByStatusInAndUpdatedAtLessThanOrderByUpdatedAtAsc(any(), any(), any())
        } returns list
    }

    private fun expiredDelivery(id: Long, status: MessageStatus) = MessageDelivery(
        id = id,
        messageId = "msg-1",
        receiverId = 2L,
        status = status,
        updatedAt = LocalDateTime.now().minusMinutes(5)
    )
}
