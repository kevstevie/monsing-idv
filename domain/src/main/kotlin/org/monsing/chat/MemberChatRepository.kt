package org.monsing.chat

import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.ne
import org.springframework.data.mongodb.core.remove
import org.springframework.stereotype.Component

@Component
class MemberChatRepository(private val mongoTemplate: MongoTemplate) {

    private val memberCache = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(10_000)
        .build<String, List<Long>>()

    fun save(memberChat: MemberChat) {
        mongoTemplate.save(memberChat)
        memberCache.invalidate(memberChat.chatId)
    }

    fun deleteByChatIdAndMemberId(chatId: String, memberId: Long) {
        val query = Query().addCriteria(
            (MemberChat::chatId isEqualTo chatId)
                .andOperator(MemberChat::memberId isEqualTo memberId)
        )
        mongoTemplate.remove<MemberChat>(query)
        memberCache.invalidate(chatId)
    }

    fun findReceiverIdByChatId(chatId: String, senderId: Long): List<Long> {
        val members = memberCache.get(chatId) { loadMemberIds(it) }
        return members.filter { it != senderId }
    }

    private fun loadMemberIds(chatId: String): List<Long> {
        val query = Query().addCriteria(MemberChat::chatId isEqualTo chatId)
        return mongoTemplate.find<MemberChat>(query).map { it.memberId }
    }

    fun saveChat(chat: Chat): Chat {
        return mongoTemplate.save(chat)
    }

    fun existByChatId(chatId: String, memberId: Long): Boolean {
        val query = Query().addCriteria(
            (MemberChat::chatId isEqualTo chatId)
                .andOperator(MemberChat::memberId isEqualTo memberId)
        )

        return mongoTemplate.exists(
            query,
            MemberChat::class.java,
        )
    }

    fun findChatByMemberId(memberId: Long): List<Chat> {
        val query = Query().addCriteria(
            MemberChat::memberId isEqualTo memberId
        )

        val chatIds = mongoTemplate.find(
            query,
            MemberChat::class.java,
        ).map { it.chatId }

        return mongoTemplate.find(
            Query().addCriteria(
                Chat::id inValues chatIds
            ),
            Chat::class.java
        )
    }

    fun findOpponentId(chatId: String, memberId: Long): Long {
        val query = Query().addCriteria(
            (MemberChat::chatId isEqualTo chatId)
                .andOperator(MemberChat::memberId ne memberId)
        )

        return mongoTemplate.findOne(
            query,
            MemberChat::class.java,
        )?.memberId ?: throw IllegalArgumentException("Opponent not found")
    }
}
