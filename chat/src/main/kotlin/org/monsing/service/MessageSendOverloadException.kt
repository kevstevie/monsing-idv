package org.monsing.service

class MessageSendOverloadException(chatId: String, cause: Throwable) :
    RuntimeException("Server is busy, please retry. chatId=$chatId", cause)
