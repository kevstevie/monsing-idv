package org.monsing.chat

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MessageDeliveryTest {

    @Test
    fun `신규 entity는 PENDING, retryCount 0`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.status shouldBe MessageStatus.PENDING
        d.retryCount shouldBe 0
    }

    @Test
    fun `transitionTo - PENDING 출발 모든 허용 path`() {
        listOf(
            MessageStatus.RELAY_PENDING,
            MessageStatus.COMPLETE,
            MessageStatus.FAILED
        ).forEach { target ->
            val d = MessageDelivery(messageId = "m", receiverId = 1L)
            d.transitionTo(target)
            d.status shouldBe target
        }
    }

    @Test
    fun `transitionTo - RELAY_PENDING 출발 허용 path (COMPLETE, FAILED)`() {
        listOf(MessageStatus.COMPLETE, MessageStatus.FAILED).forEach { target ->
            val d = MessageDelivery(messageId = "m", receiverId = 1L)
            d.transitionTo(MessageStatus.RELAY_PENDING)
            d.transitionTo(target)
            d.status shouldBe target
        }
    }

    @Test
    fun `transitionTo - FAILED 출발 허용 path (NOTIFIED, DEAD_LETTERED)`() {
        listOf(MessageStatus.NOTIFIED, MessageStatus.DEAD_LETTERED).forEach { target ->
            val d = MessageDelivery(messageId = "m", receiverId = 1L)
            d.transitionTo(MessageStatus.FAILED)
            d.transitionTo(target)
            d.status shouldBe target
        }
    }

    @Test
    fun `transitionTo - 허용 전이 시 updatedAt 갱신`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        val before = d.updatedAt
        Thread.sleep(2)

        d.transitionTo(MessageStatus.COMPLETE)

        d.updatedAt.isAfter(before) shouldBe true
    }

    @Test
    fun `transitionTo - 금지된 전이는 silent skip (status 보존)`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.COMPLETE)

        d.transitionTo(MessageStatus.FAILED)

        d.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `transitionTo - silent skip 시 updatedAt 보존 (부수효과 없음)`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.COMPLETE)
        val savedUpdatedAt = d.updatedAt
        Thread.sleep(2)

        d.transitionTo(MessageStatus.FAILED)

        d.updatedAt shouldBe savedUpdatedAt
    }

    @Test
    fun `transitionTo - silent skip 시 retryCount 보존`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.FAILED)
        d.incrementRetry()
        d.incrementRetry()

        d.transitionTo(MessageStatus.PENDING)

        d.retryCount shouldBe 2
    }

    @Test
    fun `transitionTo - PENDING에서 NOTIFIED 직접 전이 silent skip`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)

        d.transitionTo(MessageStatus.NOTIFIED)

        d.status shouldBe MessageStatus.PENDING
    }

    @Test
    fun `transitionTo - 같은 status로 멱등 호출 (terminal 보호)`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.COMPLETE)
        val savedUpdatedAt = d.updatedAt
        Thread.sleep(2)

        d.transitionTo(MessageStatus.COMPLETE)

        d.status shouldBe MessageStatus.COMPLETE
        d.updatedAt shouldBe savedUpdatedAt
    }

    @Test
    fun `incrementRetry - FAILED 상태에서만 허용`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.FAILED)

        d.incrementRetry()
        d.retryCount shouldBe 1

        d.incrementRetry()
        d.retryCount shouldBe 2
    }

    @Test
    fun `incrementRetry - status 는 FAILED 보존`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.FAILED)

        d.incrementRetry()

        d.status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `incrementRetry - updatedAt 갱신`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.FAILED)
        val before = d.updatedAt
        Thread.sleep(2)

        d.incrementRetry()

        (d.updatedAt.isAfter(before)) shouldBe true
        d.updatedAt.toLocalTime().toNanoOfDay() shouldBeGreaterThanOrEqual before.toLocalTime().toNanoOfDay()
    }

    @Test
    fun `incrementRetry - PENDING 상태에서 호출 시 예외`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)

        shouldThrow<IllegalStateException> {
            d.incrementRetry()
        }
    }

    @Test
    fun `incrementRetry - COMPLETE 상태에서 호출 시 예외`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.COMPLETE)

        shouldThrow<IllegalStateException> {
            d.incrementRetry()
        }
    }

    @Test
    fun `incrementRetry - NOTIFIED 상태에서 호출 시 예외`() {
        val d = MessageDelivery(messageId = "m-1", receiverId = 1L)
        d.transitionTo(MessageStatus.FAILED)
        d.transitionTo(MessageStatus.NOTIFIED)

        shouldThrow<IllegalStateException> {
            d.incrementRetry()
        }
    }
}
