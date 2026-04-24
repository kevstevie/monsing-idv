package org.monsing.service

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.SendResponse
import com.google.firebase.messaging.Message as FcmMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.alert.FcmTokenRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository
import org.monsing.chat.MessageRepository
import org.monsing.chat.MessageStatus
import org.springframework.data.domain.Pageable

class FcmPushNotificationSenderTest {

    private lateinit var firebaseMessaging: FirebaseMessaging
    private lateinit var fcmTokenRepository: FcmTokenRepository
    private lateinit var deliveryRepo: MessageDeliveryRepository
    private lateinit var messageRepo: MessageRepository
    private lateinit var sender: FcmPushNotificationSender

    @BeforeEach
    fun setUp() {
        firebaseMessaging = mockk(relaxed = true)
        fcmTokenRepository = mockk(relaxed = true)
        deliveryRepo = mockk(relaxed = true)
        messageRepo = mockk(relaxed = true)
        sender = FcmPushNotificationSender(firebaseMessaging, fcmTokenRepository, deliveryRepo, messageRepo)
        every { firebaseMessaging.sendEach(any()) } returns successResponse(0)
    }

    @Test
    fun `flushFailed - FAILED 없으면 아무것도 안 함`() {
        givenFailed(emptyList())

        sender.flushFailed()

        verify(exactly = 0) { firebaseMessaging.sendEach(any()) }
        verify(exactly = 0) { deliveryRepo.markNotified(any()) }
    }

    @Test
    fun `flushFailed - 토큰 보유 receiver는 FCM 발송 후 NOTIFIED 전이`() {
        val delivery = failedDelivery(id = 100L, receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hello"))
        every { fcmTokenRepository.findToken(2L) } returns setOf("token-A")
        every { firebaseMessaging.sendEach(any()) } returns successResponse(1)

        sender.flushFailed()

        val captured = slot<List<FcmMessage>>()
        verify { firebaseMessaging.sendEach(capture(captured)) }
        assert(captured.captured.size == 1)
        verify { deliveryRepo.markNotified(listOf(100L)) }
    }

    @Test
    fun `flushFailed - 토큰 없는 receiver도 NOTIFIED 전이 (무한 retry 방지)`() {
        val delivery = failedDelivery(id = 100L, receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hello"))
        every { fcmTokenRepository.findToken(2L) } returns emptySet()

        sender.flushFailed()

        verify(exactly = 0) { firebaseMessaging.sendEach(any()) }
        verify { deliveryRepo.markNotified(listOf(100L)) }
    }

    @Test
    fun `flushFailed - Message 없어도 NOTIFIED 전이 (무한 retry 방지)`() {
        val delivery = failedDelivery(id = 100L, receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns emptyList()

        sender.flushFailed()

        verify(exactly = 0) { firebaseMessaging.sendEach(any()) }
        verify { deliveryRepo.markNotified(listOf(100L)) }
    }

    @Test
    fun `flushFailed - 같은 메시지의 여러 receiver는 한 번의 message lookup`() {
        val d1 = failedDelivery(id = 100L, receiverId = 2L)
        val d2 = failedDelivery(id = 200L, receiverId = 3L)
        givenFailed(listOf(d1, d2))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        every { fcmTokenRepository.findToken(2L) } returns setOf("tok-A")
        every { fcmTokenRepository.findToken(3L) } returns setOf("tok-B")
        every { firebaseMessaging.sendEach(any()) } returns successResponse(2)

        sender.flushFailed()

        verify(exactly = 1) { messageRepo.findAllById(listOf("msg-1")) }
        verify { deliveryRepo.markNotified(listOf(100L, 200L)) }
    }

    private fun givenFailed(list: List<MessageDelivery>) {
        every {
            deliveryRepo.findAllByStatusOrderByUpdatedAtAsc(MessageStatus.FAILED, any<Pageable>())
        } returns list
    }

    private fun failedDelivery(id: Long, receiverId: Long) = MessageDelivery(
        id = id,
        messageId = "msg-1",
        receiverId = receiverId,
        status = MessageStatus.FAILED,
        updatedAt = LocalDateTime.now().minusMinutes(1)
    )

    private fun message(id: String, content: String) =
        Message(id = id, chatId = 10L, senderId = 1L, content = content)

    private fun successResponse(count: Int): BatchResponse {
        val response = mockk<BatchResponse>()
        val sendResponse = mockk<SendResponse>()
        every { sendResponse.isSuccessful } returns true
        every { response.successCount } returns count
        every { response.failureCount } returns 0
        every { response.responses } returns List(count) { sendResponse }
        return response
    }
}
