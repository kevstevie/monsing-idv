package org.monsing.service.relay

import org.monsing.chat.Message

data class RelayEnvelope(
    val receiverId: Long,
    val message: Message
)
