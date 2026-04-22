package org.monsing.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.MessageStatus
import org.monsing.chat.session.LocalSessionStorage
import org.springframework.data.repository.findByIdOrNull
import org.springframework.web.socket.WebSocketSession

class MessageRetrySchedulerTest {

    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var scheduler: MessageRetryScheduler

    @BeforeEach
    fun setUp() {
        messageDeliveryRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        localSessionStorage = mockk(relaxed = true)

        val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        scheduler = MessageRetryScheduler(
            messageDeliveryRepository = messageDeliveryRepository,
            messageRepository = messageRepository,
            localSessionStorage = localSessionStorage,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `retry - retryCount가 MAX_RETRIES 미만이면 재전송한다`() {
        val delivery = pendingDelivery(retryCount = 0)
        val session = mockk<WebSocketSession>(relaxed = true)
        val message = Message(id = "msg-1", chatId = 1L, senderId = 1L, content = "hello")

        every { messageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(any(), any(), any()) } returns listOf(delivery)
        every { messageRepository.findByIdOrNull("msg-1") } returns message
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)

        scheduler.retry()

        verify { messageDeliveryRepository.incrementRetryCount("msg-1", 2L) }
        verify { session.sendMessage(any()) }
    }

    @Test
    fun `retry - retryCount가 MAX_RETRIES 이상이면 FAILED로 전환한다`() {
        val delivery = pendingDelivery(retryCount = 3)

        every { messageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(any(), any(), any()) } returns listOf(delivery)

        scheduler.retry()

        verify { messageDeliveryRepository.updateStatus("msg-1", 2L, MessageStatus.FAILED) }
        verify(exactly = 0) { messageDeliveryRepository.incrementRetryCount(any(), any()) }
    }

    @Test
    fun `retry - 수신자별로 독립적으로 재시도한다`() {
        val delivery1 = pendingDelivery(retryCount = 0, receiverId = 2L)
        val delivery2 = pendingDelivery(retryCount = 3, receiverId = 3L)
        val session = mockk<WebSocketSession>(relaxed = true)
        val message = Message(id = "msg-1", chatId = 1L, senderId = 1L, content = "hello")

        every { messageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(any(), any(), any()) } returns listOf(delivery1, delivery2)
        every { messageRepository.findByIdOrNull("msg-1") } returns message
        every { localSessionStorage.getSessionByMemberId(2L) } returns setOf(session)

        scheduler.retry()

        verify { messageDeliveryRepository.incrementRetryCount("msg-1", 2L) }
        verify { messageDeliveryRepository.updateStatus("msg-1", 3L, MessageStatus.FAILED) }
    }

    @Test
    fun `retry - 세션이 없으면 재전송을 건너뛴다`() {
        val delivery = pendingDelivery(retryCount = 0)
        val message = Message(id = "msg-1", chatId = 1L, senderId = 1L, content = "hello")

        every { messageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(any(), any(), any()) } returns listOf(delivery)
        every { messageRepository.findByIdOrNull("msg-1") } returns message
        every { localSessionStorage.getSessionByMemberId(2L) } returns null

        scheduler.retry()

        verify { messageDeliveryRepository.incrementRetryCount("msg-1", 2L) }
    }

    @Test
    fun `retry - PENDING delivery가 없으면 아무것도 하지 않는다`() {
        every { messageDeliveryRepository.findAllByStatusAndUpdatedAtLessThan(any(), any(), any()) } returns emptyList()

        scheduler.retry()

        verify(exactly = 0) { messageDeliveryRepository.updateStatus(any(), any(), any()) }
        verify(exactly = 0) { messageDeliveryRepository.incrementRetryCount(any(), any()) }
    }

    private fun pendingDelivery(retryCount: Int, receiverId: Long = 2L) = MessageDelivery(
        messageId = "msg-1",
        receiverId = receiverId,
        retryCount = retryCount,
        updatedAt = LocalDateTime.now().minusMinutes(5)
    )
}
