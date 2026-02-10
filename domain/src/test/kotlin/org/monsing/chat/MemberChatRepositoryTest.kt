package org.monsing.chat

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find

class MemberChatRepositoryTest {

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var repository: MemberChatRepository

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        repository = MemberChatRepository(mongoTemplate)
    }

    @Test
    fun `findReceiverIdByChatId - 최초 호출 시 MongoDB 조회`() {
        every { mongoTemplate.find<MemberChat>(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = "chat-1"),
            MemberChat(memberId = 2L, chatId = "chat-1")
        )

        val result = repository.findReceiverIdByChatId("chat-1", 1L)

        result shouldBe listOf(2L)
        verify(exactly = 1) { mongoTemplate.find(any(), MemberChat::class.java) }
    }

    @Test
    fun `findReceiverIdByChatId - 두 번째 호출부터 캐시 사용, MongoDB 미조회`() {
        every { mongoTemplate.find<MemberChat>(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = "chat-1"),
            MemberChat(memberId = 2L, chatId = "chat-1")
        )

        repository.findReceiverIdByChatId("chat-1", 1L)
        repository.findReceiverIdByChatId("chat-1", 1L)
        repository.findReceiverIdByChatId("chat-1", 2L)

        verify(exactly = 1) { mongoTemplate.find<MemberChat>(any()) }
    }

    @Test
    fun `findReceiverIdByChatId - 다른 chatId는 별도 캐시`() {
        every { mongoTemplate.find<MemberChat>(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = "chat-1")
        )

        repository.findReceiverIdByChatId("chat-1", 2L)
        repository.findReceiverIdByChatId("chat-2", 2L)

        verify(exactly = 2) { mongoTemplate.find<MemberChat>(any()) }
    }

    @Test
    fun `save - 해당 chatId 캐시 무효화`() {
        every { mongoTemplate.find<MemberChat>(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = "chat-1"),
            MemberChat(memberId = 2L, chatId = "chat-1")
        )

        repository.findReceiverIdByChatId("chat-1", 1L)
        repository.save(MemberChat(memberId = 3L, chatId = "chat-1"))
        repository.findReceiverIdByChatId("chat-1", 1L)

        verify(exactly = 2) { mongoTemplate.find<MemberChat>(any()) }
    }

    @Test
    fun `deleteByChatIdAndMemberId - 해당 chatId 캐시 무효화`() {
        every { mongoTemplate.find<MemberChat>(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = "chat-1"),
            MemberChat(memberId = 2L, chatId = "chat-1")
        )

        repository.findReceiverIdByChatId("chat-1", 1L)
        repository.deleteByChatIdAndMemberId("chat-1", 2L)
        repository.findReceiverIdByChatId("chat-1", 1L)

        verify(exactly = 2) { mongoTemplate.find<MemberChat>(any()) }
    }
}
