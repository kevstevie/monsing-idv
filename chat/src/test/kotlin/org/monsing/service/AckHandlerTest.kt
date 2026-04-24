package org.monsing.service

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun `handleAck - 해당 수신자의 delivery status를 COMPLETE로 업데이트한다`() {
        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")

        verify { messageDeliveryRepository.updateStatus("msg-1", 2L, MessageStatus.COMPLETE) }
    }

    @Test
    fun `handleAck - 수신자가 다르면 각자의 delivery만 업데이트한다`() {
        ackHandler.handleAck(memberId = 2L, messageId = "msg-1")
        ackHandler.handleAck(memberId = 3L, messageId = "msg-1")

        verify { messageDeliveryRepository.updateStatus("msg-1", 2L, MessageStatus.COMPLETE) }
        verify { messageDeliveryRepository.updateStatus("msg-1", 3L, MessageStatus.COMPLETE) }
    }
}
