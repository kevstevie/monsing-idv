package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.monsing.chat.Chat
import org.monsing.chat.MemberChat
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.Message
import org.monsing.chat.MessageRepository
import org.monsing.service.relay.RedisChatRelayPublisher
import org.monsing.service.relay.RedisChatRelaySubscriber
import org.monsing.chat.session.LocalSessionStorage
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession

@Service
class ChatService(
    private val memberChatRepository: MemberChatRepository,
    private val messageRepository: MessageRepository,
) {

    fun createChat(vararg memberId: Long): Long {
        val chat = memberChatRepository.saveChat(Chat())
        val chatId = requireNotNull(chat.id) { "Saved Chat must have id" }

        memberId.forEach {
            joinChat(chatId, it)
        }
        return chatId
    }

    fun joinChat(chatId: Long, memberId: Long) {
        memberChatRepository.save(MemberChat(chatId = chatId, memberId = memberId))
    }

    fun leaveChat(chatId: Long, memberId: Long) {
        memberChatRepository.deleteByChatIdAndMemberId(chatId, memberId)
    }

    fun getMessages(chatId: Long, lastId: String?, size: Int?, memberId: Long): List<Message> {
        val isExists = memberChatRepository.existByChatId(chatId, memberId)
        require(isExists) {
            throw IllegalArgumentException("채팅방에 참여하지 않은 사용자입니다.")
        }
        return messageRepository.findByChatId(chatId, lastId, size)
    }

    fun findChatByMemberId(memberId: Long): List<Chat> {
        return memberChatRepository.findChatByMemberId(memberId)
    }

    fun findChatThumbnail(chatId: Long, memberId: Long): ThumbnailDto {
        val opp = memberChatRepository.findOpponentId(chatId, memberId)
        val lastMessage = messageRepository.findLastMessageByChatId(chatId)

        return ThumbnailDto(
            chatId = chatId,
            opponentId = opp,
            message = lastMessage
        )
    }

}

data class ThumbnailDto(
    val chatId: Long,
    val opponentId: Long,
    val message: Message?
)
