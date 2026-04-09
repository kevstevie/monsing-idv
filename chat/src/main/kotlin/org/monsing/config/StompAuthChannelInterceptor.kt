package org.monsing.config

import org.monsing.api.ChatPrincipal
import org.monsing.api.MEMBER_METADATA
import org.monsing.api.MemberMetadata
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

@Component
class StompAuthChannelInterceptor : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (accessor.command == StompCommand.CONNECT) {
            val metadata = accessor.sessionAttributes?.get(MEMBER_METADATA) as? MemberMetadata
                ?: throw IllegalArgumentException("MemberMetadata not found in session attributes")
            accessor.user = ChatPrincipal(metadata.memberId)
        }

        return message
    }
}
