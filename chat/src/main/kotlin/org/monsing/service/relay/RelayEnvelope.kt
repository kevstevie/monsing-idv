package org.monsing.service.relay

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.monsing.chat.Message

@JsonIgnoreProperties(ignoreUnknown = true)
data class RelayEnvelope(
    val receiverIds: List<Long>,
    val message: Message
) {
    companion object {
        @JvmStatic
        @JsonCreator
        fun create(
            @JsonProperty("receiverId") legacyReceiverId: Long?,
            @JsonProperty("receiverIds") receiverIds: List<Long>?,
            @JsonProperty("message") message: Message
        ): RelayEnvelope {
            val ids = receiverIds ?: legacyReceiverId?.let { listOf(it) } ?: emptyList()
            return RelayEnvelope(ids, message)
        }
    }
}
