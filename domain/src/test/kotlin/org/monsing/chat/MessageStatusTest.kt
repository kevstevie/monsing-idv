package org.monsing.chat

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MessageStatusTest {

    @Test
    fun `PENDING은 RELAY_PENDING, COMPLETE, FAILED 로 전이 가능`() {
        MessageStatus.PENDING.canTransitionTo(MessageStatus.RELAY_PENDING) shouldBe true
        MessageStatus.PENDING.canTransitionTo(MessageStatus.COMPLETE) shouldBe true
        MessageStatus.PENDING.canTransitionTo(MessageStatus.FAILED) shouldBe true
    }

    @Test
    fun `PENDING은 NOTIFIED, DEAD_LETTERED, PENDING 자기 자신으로 전이 불가`() {
        MessageStatus.PENDING.canTransitionTo(MessageStatus.NOTIFIED) shouldBe false
        MessageStatus.PENDING.canTransitionTo(MessageStatus.DEAD_LETTERED) shouldBe false
        MessageStatus.PENDING.canTransitionTo(MessageStatus.PENDING) shouldBe false
    }

    @Test
    fun `RELAY_PENDING은 COMPLETE, FAILED 로만 전이 가능`() {
        MessageStatus.RELAY_PENDING.canTransitionTo(MessageStatus.COMPLETE) shouldBe true
        MessageStatus.RELAY_PENDING.canTransitionTo(MessageStatus.FAILED) shouldBe true

        MessageStatus.RELAY_PENDING.canTransitionTo(MessageStatus.PENDING) shouldBe false
        MessageStatus.RELAY_PENDING.canTransitionTo(MessageStatus.NOTIFIED) shouldBe false
        MessageStatus.RELAY_PENDING.canTransitionTo(MessageStatus.DEAD_LETTERED) shouldBe false
    }

    @Test
    fun `FAILED는 NOTIFIED, DEAD_LETTERED 로만 전이 가능`() {
        MessageStatus.FAILED.canTransitionTo(MessageStatus.NOTIFIED) shouldBe true
        MessageStatus.FAILED.canTransitionTo(MessageStatus.DEAD_LETTERED) shouldBe true

        MessageStatus.FAILED.canTransitionTo(MessageStatus.PENDING) shouldBe false
        MessageStatus.FAILED.canTransitionTo(MessageStatus.RELAY_PENDING) shouldBe false
        MessageStatus.FAILED.canTransitionTo(MessageStatus.COMPLETE) shouldBe false
    }

    @Test
    fun `COMPLETE, NOTIFIED, DEAD_LETTERED는 terminal - 어떤 상태로도 전이 불가`() {
        val terminals = listOf(MessageStatus.COMPLETE, MessageStatus.NOTIFIED, MessageStatus.DEAD_LETTERED)
        terminals.forEach { from ->
            MessageStatus.entries.forEach { to ->
                from.canTransitionTo(to) shouldBe false
            }
        }
    }
}
