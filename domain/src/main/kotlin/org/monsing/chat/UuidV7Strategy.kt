package org.monsing.chat

import com.github.f4b6a3.uuid.UuidCreator
import org.springframework.stereotype.Component

@Component
class UuidV7Strategy : MessageIdStrategy {

    override fun generateId(message: Message) {
        message.id = UuidCreator.getTimeOrderedEpoch().toString()
    }
}
