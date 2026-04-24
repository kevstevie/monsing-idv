package org.monsing.chat

import org.springframework.data.jpa.repository.JpaRepository

interface JpaChatRepository : JpaRepository<Chat, Long>
