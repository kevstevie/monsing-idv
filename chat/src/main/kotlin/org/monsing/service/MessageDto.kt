package org.monsing.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageDto(
    val chatId: String,
    val content: String,
    val clientMessageId: String? = null
)
