package org.monsing.api

import java.security.Principal

data class ChatPrincipal(val memberId: Long) : Principal {
    override fun getName(): String = memberId.toString()
}
