package org.monsing.chat

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberChatRepository(
    private val jpaMemberChatRepository: JpaMemberChatRepository,
    private val jpaChatRepository: JpaChatRepository
) {

    private val memberCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(10_000)
        .build<Long, List<Long>>()

    fun save(memberChat: MemberChat) {
        jpaMemberChatRepository.save(memberChat)
        memberCache.invalidate(memberChat.chatId)
    }

    @Transactional
    fun deleteByChatIdAndMemberId(chatId: Long, memberId: Long) {
        jpaMemberChatRepository.deleteByChatIdAndMemberId(chatId, memberId)
        memberCache.invalidate(chatId)
    }

    fun findReceiverIdByChatId(chatId: Long, senderId: Long): List<Long> {
        val members = memberCache.get(chatId) { loadMemberIds(it) }
        return members.filter { it != senderId }
    }

    private fun loadMemberIds(chatId: Long): List<Long> {
        return jpaMemberChatRepository.findAllByChatId(chatId).map { it.memberId }
    }

    fun saveChat(chat: Chat): Chat {
        return jpaChatRepository.save(chat)
    }

    fun existByChatId(chatId: Long, memberId: Long): Boolean {
        return jpaMemberChatRepository.existsByChatIdAndMemberId(chatId, memberId)
    }

    fun findChatByMemberId(memberId: Long): List<Chat> {
        val chatIds = jpaMemberChatRepository.findAllByMemberId(memberId).map { it.chatId }
        if (chatIds.isEmpty()) return emptyList()
        return jpaChatRepository.findAllById(chatIds)
    }

    fun findOpponentId(chatId: Long, memberId: Long): Long {
        return jpaMemberChatRepository.findFirstByChatIdAndMemberIdNot(chatId, memberId)?.memberId
            ?: throw IllegalArgumentException("Opponent not found")
    }
}
