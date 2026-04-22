package org.monsing.chat

import org.springframework.data.jpa.repository.JpaRepository

interface MessageReceivedRepository : JpaRepository<MessageReceived, String>
