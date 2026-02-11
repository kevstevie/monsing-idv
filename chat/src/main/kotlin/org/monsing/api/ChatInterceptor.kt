package org.monsing.api

import org.monsing.auth.jwt.AuthTokenManager
import org.monsing.chat.session.LocalSessionStorage
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

private const val TOKEN = "token"
private const val DEVICE_ID = "device-id"

@Component
class ChatInterceptor(
    private val authTokenManager: AuthTokenManager,
    private val localSessionStorage: LocalSessionStorage
) : HandshakeInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (localSessionStorage.size >= MAX_SESSIONS) {
            log.warn("WebSocket connection rejected: server full ({} sessions)", MAX_SESSIONS)
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE)
            return false
        }

        val query = UriComponentsBuilder.fromUri(request.uri).build().queryParams

        val memberId = query[TOKEN]?.firstOrNull()
            ?.let { authTokenManager.getPayLoad(it).id }
            ?: throw IllegalArgumentException("Member id must not be null")

        val deviceId = requireNotNull(query[DEVICE_ID]?.firstOrNull()) {
            "Device id must not be null"
        }

        attributes[MEMBER_METADATA] = MemberMetadata(memberId, deviceId)

        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) = Unit

    companion object {
        private const val MAX_SESSIONS = 10_000
    }
}
