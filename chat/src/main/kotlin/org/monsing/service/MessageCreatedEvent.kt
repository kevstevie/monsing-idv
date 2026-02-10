package org.monsing.service

import org.monsing.chat.Message

data class MessageCreatedEvent(
    val message: Message
)
