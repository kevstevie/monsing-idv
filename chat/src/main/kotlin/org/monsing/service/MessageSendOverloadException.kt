package org.monsing.service

class MessageSendOverloadException(chatId: Long, cause: Throwable) :
    RuntimeException("Server is busy, please retry. chatId=$chatId", cause)
