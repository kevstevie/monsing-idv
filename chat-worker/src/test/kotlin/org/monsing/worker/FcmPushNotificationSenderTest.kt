package org.monsing.worker

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.SendResponse
import com.google.firebase.messaging.Message as FcmMessage
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
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
    }

    @Test
    fun `flushFailed - 토큰 보유 receiver 는 FCM 발송 후 NOTIFIED 전이`() {
        val delivery = failedDelivery(receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hello"))
        givenTokens(2L to setOf("token-A"))
        every { firebaseMessaging.sendEach(any()) } returns successResponse(1)

        sender.flushFailed()

        delivery.status shouldBe MessageStatus.NOTIFIED
        delivery.retryCount shouldBe 0
    }

    @Test
    fun `flushFailed - 토큰 없는 receiver 도 NOTIFIED 전이 (무한 retry 방지)`() {
        val delivery = failedDelivery(receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hello"))
        givenTokens()

        sender.flushFailed()

        verify(exactly = 0) { firebaseMessaging.sendEach(any()) }
        delivery.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `flushFailed - Message 없어도 NOTIFIED 전이`() {
        val delivery = failedDelivery(receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns emptyList()

        sender.flushFailed()

        verify(exactly = 0) { firebaseMessaging.sendEach(any()) }
        delivery.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `flushFailed - 같은 메시지의 여러 receiver 는 한 번의 message lookup`() {
        val d1 = failedDelivery(receiverId = 2L)
        val d2 = failedDelivery(receiverId = 3L)
        givenFailed(listOf(d1, d2))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-A"), 3L to setOf("tok-B"))
        every { firebaseMessaging.sendEach(any()) } returns successResponse(2)

        sender.flushFailed()

        verify(exactly = 1) { messageRepo.findAllById(listOf("msg-1")) }
        verify(exactly = 1) { fcmTokenRepository.findTokens(listOf(2L, 3L)) }
        d1.status shouldBe MessageStatus.NOTIFIED
        d2.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `flushFailed - 발송 예외 시 retryCount 증가, status 는 FAILED 유지`() {
        val delivery = failedDelivery(receiverId = 2L, retryCount = 0)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-A"))
        every { firebaseMessaging.sendEach(any()) } throws IOException("FCM service unavailable")

        sender.flushFailed()

        delivery.status shouldBe MessageStatus.FAILED
        delivery.retryCount shouldBe 1
    }

    @Test
    fun `flushFailed - retryCount 가 MAX 직전일 때 예외 발생 시 DEAD_LETTERED`() {
        val delivery = failedDelivery(receiverId = 2L, retryCount = MAX_ATTEMPTS - 1)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-A"))
        every { firebaseMessaging.sendEach(any()) } throws IOException("FCM service unavailable")

        sender.flushFailed()

        delivery.status shouldBe MessageStatus.DEAD_LETTERED
        delivery.retryCount shouldBe MAX_ATTEMPTS - 1
    }

    @Test
    fun `flushFailed - 토큰 없는 NOTIFIED 와 재시도 대상이 한 tick 에 분리 처리`() {
        val noToken = failedDelivery(receiverId = 1L, retryCount = 0)
        val retryable = failedDelivery(receiverId = 2L, retryCount = 0)
        givenFailed(listOf(noToken, retryable))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-A"))
        every { firebaseMessaging.sendEach(any()) } throws IOException("FCM outage")

        sender.flushFailed()

        noToken.status shouldBe MessageStatus.NOTIFIED
        retryable.status shouldBe MessageStatus.FAILED
        retryable.retryCount shouldBe 1
    }

    @Test
    fun `flushFailed - 한 receiver 에 다중 토큰 이면 토큰 수만큼 FcmMessage 생성`() {
        val delivery = failedDelivery(receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("phone", "tablet", "desktop"))
        every { firebaseMessaging.sendEach(any()) } returns successResponse(3)

        sender.flushFailed()

        val captured = slot<List<FcmMessage>>()
        verify { firebaseMessaging.sendEach(capture(captured)) }
        captured.captured.size shouldBe 3
        delivery.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `flushFailed - chunk 중 한 batch 라도 실패하면 retry 대상`() {
        val delivery = failedDelivery(receiverId = 2L, retryCount = 0)
        val manyTokens = (1..MAX_BATCH_SIZE + 50).map { "tok-$it" }.toSet()
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to manyTokens)
        every { firebaseMessaging.sendEach(any()) } returns successResponse(MAX_BATCH_SIZE) andThenThrows IOException("second batch down")

        sender.flushFailed()

        delivery.status shouldBe MessageStatus.FAILED
        delivery.retryCount shouldBe 1
    }

    @Test
    fun `flushFailed - 부분 배치 실패 (failureCount 0 초과 exception 없음) 는 NOTIFIED`() {
        val delivery = failedDelivery(receiverId = 2L)
        givenFailed(listOf(delivery))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("bad-token", "good-token"))
        every { firebaseMessaging.sendEach(any()) } returns mixedResponse(success = 1, failure = 1)

        sender.flushFailed()

        delivery.status shouldBe MessageStatus.NOTIFIED
    }

    @Test
    fun `flushFailed - 3-way mixed tick (NOTIFY + retry + DEAD_LETTERED) 분리 적용`() {
        val noToken = failedDelivery(receiverId = 1L, retryCount = 0)
        val retryable = failedDelivery(receiverId = 2L, retryCount = 0)
        val nearMax = failedDelivery(receiverId = 3L, retryCount = MAX_ATTEMPTS - 1)
        givenFailed(listOf(noToken, retryable, nearMax))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-B"), 3L to setOf("tok-C"))
        every { firebaseMessaging.sendEach(any()) } throws IOException("outage")

        sender.flushFailed()

        noToken.status shouldBe MessageStatus.NOTIFIED
        retryable.status shouldBe MessageStatus.FAILED
        retryable.retryCount shouldBe 1
        nearMax.status shouldBe MessageStatus.DEAD_LETTERED
    }

    @Test
    fun `flushFailed - 같은 tick 에서 성공 delivery 와 실패 delivery 동시 처리`() {
        val ok = failedDelivery(receiverId = 2L)
        val fail = failedDelivery(receiverId = 3L, retryCount = 0)
        givenFailed(listOf(ok, fail))
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(2L to setOf("tok-OK"), 3L to setOf("tok-FAIL"))
        every { firebaseMessaging.sendEach(any()) } returns successResponse(1) andThenThrows IOException("target outage")

        sender.flushFailed()

        ok.status shouldBe MessageStatus.NOTIFIED
        fail.status shouldBe MessageStatus.FAILED
        fail.retryCount shouldBe 1
    }

    @Test
    fun `flushFailed - receiver N 명이어도 토큰 조회는 단일 호출`() {
        val deliveries = (1L..10L).map { failedDelivery(receiverId = it) }
        givenFailed(deliveries)
        every { messageRepo.findAllById(listOf("msg-1")) } returns listOf(message("msg-1", "hi"))
        givenTokens(*(1L..10L).map { it to setOf("tok-$it") }.toTypedArray())
        every { firebaseMessaging.sendEach(any()) } returns successResponse(10)

        sender.flushFailed()

        verify(exactly = 1) { fcmTokenRepository.findTokens(any()) }
    }

    @Test
    fun `flushFailed - BATCH_LIMIT 으로 Pageable size 제한 전달`() {
        givenFailed(emptyList())

        sender.flushFailed()

        val pageable = slot<Pageable>()
        verify {
            deliveryRepo.findAllByStatusOrderByUpdatedAtAsc(MessageStatus.FAILED, capture(pageable))
        }
        pageable.captured.pageSize shouldBe BATCH_LIMIT
    }

    private fun givenFailed(list: List<MessageDelivery>) {
        every {
            deliveryRepo.findAllByStatusOrderByUpdatedAtAsc(MessageStatus.FAILED, any<Pageable>())
        } returns list
    }

    private fun givenTokens(vararg entries: Pair<Long, Set<String>>) {
        every { fcmTokenRepository.findTokens(any()) } returns entries.toMap()
    }

    private fun failedDelivery(receiverId: Long, retryCount: Int = 0): MessageDelivery {
        val d = MessageDelivery(messageId = "msg-1", receiverId = receiverId)
        d.transitionTo(MessageStatus.FAILED)
        repeat(retryCount) { d.incrementRetry() }
        return d
    }

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

    private fun mixedResponse(success: Int, failure: Int): BatchResponse {
        val response = mockk<BatchResponse>()
        val successRes = mockk<SendResponse>()
        val failureRes = mockk<SendResponse>()
        every { successRes.isSuccessful } returns true
        every { successRes.exception } returns null
        every { failureRes.isSuccessful } returns false
        every { failureRes.exception } returns null
        every { response.successCount } returns success
        every { response.failureCount } returns failure
        every { response.responses } returns List(success) { successRes } + List(failure) { failureRes }
        return response
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val MAX_BATCH_SIZE = 500
        private const val BATCH_LIMIT = 500
    }
}
