//package org.monsing.application
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import io.mockk.every
//import io.mockk.junit5.MockKExtension
//import io.mockk.mockk
//import io.mockk.spyk
//import io.mockk.verify
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.extension.ExtendWith
//import org.monsing.service.ChatService
//import org.monsing.chat.MemberChatRepository
//import org.monsing.service.MessageDto
//import org.monsing.chat.MessageRepository
//import org.monsing.service.relay.RedisChatRelayPublisher
//import org.monsing.service.relay.RedisChatRelaySubscriber
//import org.monsing.chat.session.LocalSessionStorage
//import org.springframework.web.socket.TextMessage
//import org.springframework.web.socket.WebSocketSession
//
//@ExtendWith(MockKExtension::class)
//class ChatServiceTest {
//
//    private val localSessionStorage = mockk<LocalSessionStorage>(relaxed = true)
//    private val redisChatRelayPublisher = mockk<RedisChatRelayPublisher>(relaxed = true)
//    private val redisChatRelaySubscriber = mockk<RedisChatRelaySubscriber>(relaxed = true)
//    private val memberChatRepository = mockk<MemberChatRepository>() {
//        every { findReceiverIdByChatId(any(), any()) } returns listOf(1L)
//    }
//    private val messageRepository = mockk<MessageRepository>(relaxed = true)
//
//    private val objectMapper = mockk<ObjectMapper>(relaxed = true) {
//        every { readValue(any(String::class), any<Class<*>>()) } returns MessageDto("1", "Hello")
//    }
//
//    private val chatService = spyk<ChatService>(
//        objToCopy = ChatService(
//            localSessionStorage,
//            memberChatRepository,
//            messageRepository,
//            objectMapper,
//            redisChatRelayPublisher,
//            redisChatRelaySubscriber
//        ),
//        recordPrivateCalls = true
//    )
//
//    @Test
//    fun `로컬 세션이 없을 때 온라인 확인 후 Pub_Sub으로 발행한다`() {
//        // given
//        val receiverId = 1L
//        every { localSessionStorage.getSessionByMemberId(receiverId) } returns null
//        every { redisChatRelayPublisher.isUserOnline(receiverId) } returns true
//
//        // when
//        chatService.handleMessage(1L, TextMessage(""))
//
//        // then
//        verify { redisChatRelayPublisher.isUserOnline(receiverId) }
//        verify { redisChatRelayPublisher.publishToUser(receiverId, any()) }
//    }
//
//    @Test
//    fun `유저가 오프라인이면 푸시 이벤트를 발행한다`() {
//        // given
//        val receiverId = 1L
//        every { localSessionStorage.getSessionByMemberId(receiverId) } returns null
//        every { redisChatRelayPublisher.isUserOnline(receiverId) } returns false
//
//        // when
//        chatService.handleMessage(1L, TextMessage(""))
//
//        // then
//        verify { redisChatRelayPublisher.isUserOnline(receiverId) }
//        verify { chatService["publishMessageSentEvent"]() }
//    }
//
//    @Test
//    fun `첫 세션 저장 시 Pub_Sub 구독을 시작한다`() {
//        // given
//        val memberId = 1L
//        val deviceId = "device1"
//        val session = mockk<WebSocketSession>(relaxed = true)
//        every { localSessionStorage.getSessionByMemberId(memberId) } returns null
//
//        // when
//        chatService.saveSession(memberId, deviceId, session)
//
//        // then
//        verify { redisChatRelaySubscriber.subscribe(memberId) }
//    }
//
//    @Test
//    fun `모든 세션 제거 시 Pub_Sub 구독을 해제한다`() {
//        // given
//        val memberId = 1L
//        val deviceId = "device1"
//        every { localSessionStorage.getSessionByMemberId(memberId) } returns null
//
//        // when
//        chatService.removeSession(memberId, deviceId)
//
//        // then
//        verify { redisChatRelaySubscriber.unsubscribe(memberId) }
//    }
//
//    @Test
//    fun `멀티디바이스 환경에서 2번째 세션은 구독하지 않는다`() {
//        // given
//        val memberId = 1L
//        val session1 = mockk<WebSocketSession>(relaxed = true)
//        val session2 = mockk<WebSocketSession>(relaxed = true)
//
//        // 첫 세션 저장
//        every { localSessionStorage.getSessionByMemberId(memberId) } returns null
//        chatService.saveSession(memberId, "device1", session1)
//
//        // 두 번째 세션 저장 시에는 이미 세션 있음
//        every { localSessionStorage.getSessionByMemberId(memberId) } returns setOf(session1)
//
//        // when
//        chatService.saveSession(memberId, "device2", session2)
//
//        // then
//        verify(exactly = 1) { redisChatRelaySubscriber.subscribe(memberId) }
//    }
//
//    @Test
//    fun `로컬 세션이 있을 때 Pub_Sub 확인 없이 즉시 전송한다`() {
//        // given
//        val receiverId = 1L
//        val session = mockk<WebSocketSession>(relaxed = true)
//        every { localSessionStorage.getSessionByMemberId(receiverId) } returns setOf(session)
//
//        // when
//        chatService.handleMessage(1L, TextMessage(""))
//
//        // then
//        verify { session.sendMessage(any()) }
//        verify(exactly = 0) { redisChatRelayPublisher.isUserOnline(any()) }
//    }
//
//    @Test
//    fun `message를 relay할 때 local에 session이 있다면 message를 전송한다`() {
//        // given
//        val receiverId = 1L
//        val session = mockk<WebSocketSession>(relaxed = true)
//        every { localSessionStorage.getSessionByMemberId(receiverId) } returns setOf(session)
//
//        // when
//        chatService.handleMessage(receiverId, TextMessage(""))
//
//        // then
//        verify { session.sendMessage(any()) }
//    }
//}
