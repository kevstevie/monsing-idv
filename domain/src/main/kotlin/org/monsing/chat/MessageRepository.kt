package org.monsing.chat

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MessageRepository : JpaRepository<Message, String> {

    fun findTopByChatIdOrderByIdDesc(chatId: Long): Message?

    @Query(
        """
        select m from Message m
         where m.chatId = :chatId
           and (:lastId is null or m.id < :lastId)
         order by m.id desc
        """
    )
    fun findPageByChatId(
        @Param("chatId") chatId: Long,
        @Param("lastId") lastId: String?,
        pageable: Pageable
    ): List<Message>
}
