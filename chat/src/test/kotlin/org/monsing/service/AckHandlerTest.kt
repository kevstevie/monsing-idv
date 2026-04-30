package org.monsing.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageStatus

class AckHandlerTest {

    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var ackHandler: AckHandler

    @BeforeEach
    fun setUp() {
        messageDeliveryRepository = mockk(relaxed = true)
        ackHandler = AckHandler(messageDeliveryRepository)
    }

    @Test
    fun `handleAck - PENDING 이면 COMPLETE 로 전이`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `handleAck - RELAY_PENDING 도 COMPLETE 로 전이`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.RELAY_PENDING)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `handleAck - 이미 COMPLETE 면 그대로 (멱등)`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.COMPLETE)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.COMPLETE
    }

    @Test
    fun `handleAck - FAILED 면 COMPLETE 로 전이 차단`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.FAILED)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.FAILED
    }

    @Test
    fun `handleAck - NOTIFIED terminal 도 COMPLETE 로 전이 차단`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.FAILED)
        delivery.transitionTo(MessageStatus.NOTIFIED)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `handleAck - DEAD_LETTERED terminal 도 COMPLETE 로 전이 차단`() {
        val delivery = MessageDelivery(messageId = "msg-1", receiverId = 2L)
        delivery.transitionTo(MessageStatus.FAILED)
        delivery.transitionTo(MessageStatus.DEAD_LETTERED)
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("msg-1", 2L) } returns delivery

        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        delivery.status shouldBe MessageStatus.DEAD_LETTERED
    }

    @Test
    fun `handleAck - 매칭 row 없으면 무시`() {
        every { messageDeliveryRepository.findByMessageIdAndReceiverId("missing", 2L) } returns null

        ackHandler.handleAck(memberId = 2L, messageId = "missing")
    }
}
