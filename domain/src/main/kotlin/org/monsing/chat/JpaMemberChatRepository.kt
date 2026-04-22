package org.monsing.chat

import org.springframework.data.jpa.repository.JpaRepository

interface JpaMemberChatRepository : JpaRepository<MemberChat, Long> {

    fun findAllByChatId(chatId: Long): List<MemberChat>

    fun findAllByMemberId(memberId: Long): List<MemberChat>

    fun existsByChatIdAndMemberId(chatId: Long, memberId: Long): Boolean

    fun deleteByChatIdAndMemberId(chatId: Long, memberId: Long)

    fun findFirstByChatIdAndMemberIdNot(chatId: Long, memberId: Long): MemberChat?
}
