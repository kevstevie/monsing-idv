package org.monsing.chat

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemberChatRepositoryTest {

    private lateinit var jpaMemberChatRepository: JpaMemberChatRepository
    private lateinit var jpaChatRepository: JpaChatRepository
    private lateinit var repository: MemberChatRepository

    @BeforeEach
    fun setUp() {
        jpaMemberChatRepository = mockk(relaxed = true)
        jpaChatRepository = mockk(relaxed = true)
        every { jpaMemberChatRepository.save(any<MemberChat>()) } answers { firstArg() }
        every { jpaChatRepository.save(any<Chat>()) } answers { firstArg() }
        repository = MemberChatRepository(jpaMemberChatRepository, jpaChatRepository)
    }

    @Test
    fun `findReceiverIdByChatId - 최초 호출 시 DB 조회`() {
        every { jpaMemberChatRepository.findAllByChatId(1L) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L),
            MemberChat(memberId = 2L, chatId = 1L)
        )

        val result = repository.findReceiverIdByChatId(1L, 1L)

        result shouldBe listOf(2L)
        verify(exactly = 1) { jpaMemberChatRepository.findAllByChatId(1L) }
    }

    @Test
    fun `findReceiverIdByChatId - 두 번째 호출부터 캐시 사용, DB 미조회`() {
        every { jpaMemberChatRepository.findAllByChatId(1L) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L),
            MemberChat(memberId = 2L, chatId = 1L)
        )

        repository.findReceiverIdByChatId(1L, 1L)
        repository.findReceiverIdByChatId(1L, 1L)
        repository.findReceiverIdByChatId(1L, 2L)

        verify(exactly = 1) { jpaMemberChatRepository.findAllByChatId(1L) }
    }

    @Test
    fun `findReceiverIdByChatId - 다른 chatId는 별도 캐시`() {
        every { jpaMemberChatRepository.findAllByChatId(any()) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L)
        )

        repository.findReceiverIdByChatId(1L, 2L)
        repository.findReceiverIdByChatId(2L, 2L)

        verify(exactly = 1) { jpaMemberChatRepository.findAllByChatId(1L) }
        verify(exactly = 1) { jpaMemberChatRepository.findAllByChatId(2L) }
    }

    @Test
    fun `save - 해당 chatId 캐시 무효화`() {
        every { jpaMemberChatRepository.findAllByChatId(1L) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L),
            MemberChat(memberId = 2L, chatId = 1L)
        )

        repository.findReceiverIdByChatId(1L, 1L)
        repository.save(MemberChat(memberId = 3L, chatId = 1L))
        repository.findReceiverIdByChatId(1L, 1L)

        verify(exactly = 2) { jpaMemberChatRepository.findAllByChatId(1L) }
    }

    @Test
    fun `deleteByChatIdAndMemberId - 해당 chatId 캐시 무효화`() {
        every { jpaMemberChatRepository.findAllByChatId(1L) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L),
            MemberChat(memberId = 2L, chatId = 1L)
        )

        repository.findReceiverIdByChatId(1L, 1L)
        repository.deleteByChatIdAndMemberId(1L, 2L)
        repository.findReceiverIdByChatId(1L, 1L)

        verify(exactly = 2) { jpaMemberChatRepository.findAllByChatId(1L) }
        verify(exactly = 1) { jpaMemberChatRepository.deleteByChatIdAndMemberId(1L, 2L) }
    }

    @Test
    fun `findChatByMemberId - 해당 멤버의 채팅방 없으면 빈 리스트`() {
        every { jpaMemberChatRepository.findAllByMemberId(1L) } returns emptyList()

        val result = repository.findChatByMemberId(1L)

        result shouldBe emptyList()
        verify { jpaChatRepository wasNot Called }
    }

    @Test
    fun `findChatByMemberId - 해당 멤버의 모든 채팅방 반환`() {
        val chat1 = Chat(id = 1L)
        val chat2 = Chat(id = 2L)
        every { jpaMemberChatRepository.findAllByMemberId(1L) } returns listOf(
            MemberChat(memberId = 1L, chatId = 1L),
            MemberChat(memberId = 1L, chatId = 2L)
        )
        every { jpaChatRepository.findAllById(listOf(1L, 2L)) } returns listOf(chat1, chat2)

        val result = repository.findChatByMemberId(1L)

        result.map { it.id }.shouldContainExactlyInAnyOrder(1L, 2L)
    }
}
