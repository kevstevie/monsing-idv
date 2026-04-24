package org.monsing.api

import org.monsing.service.MessageDto

sealed interface InboundFrame {
    data class Chat(val message: MessageDto) : InboundFrame
    data class Ack(val messageId: String) : InboundFrame
}
