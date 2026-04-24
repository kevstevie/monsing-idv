package org.monsing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageReceived
import org.monsing.chat.MessageReceivedRepository
import org.monsing.chat.MessageRepository
import org.springframework.data.repository.findByIdOrNull

class MessageInboxServiceTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var messageReceivedRepository: MessageReceivedRepository
    private lateinit var messageIdStrategy: MessageIdStrategy
    private lateinit var ackSender: AckSender
    private lateinit var service: MessageInboxService

    @BeforeEach
    fun setUp() {
        messageRepository = mockk(relaxed = true)
        messageReceivedRepository = mockk(relaxed = true)
        messageIdStrategy = mockk(relaxed = true)
        ackSender = mockk(relaxed = true)

        every { messageRepository.save(any()) } answers { firstArg() }
        every { messageReceivedRepository.save(any()) } answers { firstArg() }
        every { messageIdStrategy.generateId(any()) } answers {
            firstArg<Message>().id = "m-1"
        }

        service = MessageInboxService(
            messageRepository, messageReceivedRepository, messageIdStrategy, ackSender
        )
    }

    @Test
    fun `resendAckIfDuplicate - MessageReceived 있으면 ACK 재전송 후 true 반환`() {
        every { messageReceivedRepository.findByIdOrNull("cid-1") } returns
            MessageReceived(clientMessageId = "cid-1", messageId = "old-msg-id", senderId = 1L, chatId = 7L)

        val handled = service.resendAckIfDuplicate(1L, MessageDto(chatId = 7L, content = "x", clientMessageId = "cid-1"))

        assertTrue(handled)
        verify(exactly = 1) { ackSender.sendOrThrow(1L, 7L, "old-msg-id", "cid-1") }
    }

    @Test
    fun `resendAckIfDuplicate - clientMessageId가 null이면 false`() {
        val handled = service.resendAckIfDuplicate(1L, MessageDto(chatId = 1L, content = "x"))

        assertFalse(handled)
        verify(exactly = 0) { ackSender.sendOrThrow(any(), any(), any(), any()) }
    }

    @Test
    fun `resendAckIfDuplicate - MessageReceived가 없으면 false`() {
        every { messageReceivedRepository.findByIdOrNull("cid-x") } returns null

        val handled = service.resendAckIfDuplicate(1L, MessageDto(chatId = 1L, content = "x", clientMessageId = "cid-x"))

        assertFalse(handled)
        verify(exactly = 0) { ackSender.sendOrThrow(any(), any(), any(), any()) }
    }

    @Test
    fun `resendAckIfDuplicate - ACK 전송 실패 시 예외 전파`() {
        every { messageReceivedRepository.findByIdOrNull("cid-1") } returns
            MessageReceived(clientMessageId = "cid-1", messageId = "m-1", senderId = 1L, chatId = 1L)
        every {
            ackSender.sendOrThrow(1L, 1L, "m-1", "cid-1")
        } throws AckDeliveryFailedException(1L, 1L, "m-1", "cid-1")

        assertThrows(AckDeliveryFailedException::class.java) {
            service.resendAckIfDuplicate(1L, MessageDto(chatId = 1L, content = "x", clientMessageId = "cid-1"))
        }
    }

    @Test
    fun `persistAndAck - id 생성, Message 저장, MessageReceived 저장, ACK 전송 순서`() {
        service.persistAndAck(1L, MessageDto(chatId = 1L, content = "hi", clientMessageId = "cid-1"))

        verifyOrder {
            messageIdStrategy.generateId(any())
            messageRepository.save(match<Message> { it.id == "m-1" })
            messageReceivedRepository.save(
                match<MessageReceived> { it.clientMessageId == "cid-1" && it.messageId == "m-1" }
            )
            ackSender.sendOrThrow(1L, 1L, "m-1", "cid-1")
        }
    }

    @Test
    fun `persistAndAck - clientMessageId 없으면 MessageReceived 저장과 ACK 전송을 하지 않는다`() {
        service.persistAndAck(1L, MessageDto(chatId = 1L, content = "hi"))

        verify(exactly = 1) { messageRepository.save(any()) }
        verify(exactly = 0) { messageReceivedRepository.save(any()) }
        verify(exactly = 0) { ackSender.sendOrThrow(any(), any(), any(), any()) }
    }

    @Test
    fun `persistAndAck - ACK 전송 실패 시 AckDeliveryFailedException 전파`() {
        every {
            ackSender.sendOrThrow(1L, 1L, "m-1", "cid-1")
        } throws AckDeliveryFailedException(1L, 1L, "m-1", "cid-1")

        assertThrows(AckDeliveryFailedException::class.java) {
            service.persistAndAck(1L, MessageDto(chatId = 1L, content = "hi", clientMessageId = "cid-1"))
        }
    }

    @Test
    fun `persistAndAck - 생성된 Message 를 반환한다`() {
        val result = service.persistAndAck(1L, MessageDto(chatId = 2L, content = "hi"))

        assertEquals("m-1", result.id)
        assertEquals(2L, result.chatId)
        assertEquals(1L, result.senderId)
        assertEquals("hi", result.content)
    }
}
