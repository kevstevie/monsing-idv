package org.monsing.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.session.LocalSessionStorage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class AckSenderTest {

    private lateinit var localSessionStorage: LocalSessionStorage
    private lateinit var objectMapper: ObjectMapper
    private lateinit var sender: AckSender

    @BeforeEach
    fun setUp() {
        localSessionStorage = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper()
        sender = AckSender(localSessionStorage, objectMapper)
    }

    @Test
    fun `sendOrThrow - 세션이 없으면 AckDeliveryFailedException 발생`() {
        every { localSessionStorage.getSessionByMemberId(1L) } returns null

        assertThrows(AckDeliveryFailedException::class.java) {
            sender.sendOrThrow(1L, 1L, "m-1", "cid-1")
        }
    }

    @Test
    fun `sendOrThrow - 세션은 있지만 모두 전송 실패 시 예외 발생`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(session)
        every { session.sendMessage(any()) } throws java.io.IOException("connection reset")
        every { localSessionStorage.removeSession(session) } just runs

        assertThrows(AckDeliveryFailedException::class.java) {
            sender.sendOrThrow(1L, 1L, "m-1", "cid-1")
        }

        verify(exactly = 1) { localSessionStorage.removeSession(session) }
    }

    @Test
    fun `sendOrThrow - 세션 중 하나라도 성공하면 예외 없이 완료`() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { localSessionStorage.getSessionByMemberId(1L) } returns setOf(session)
        every { session.sendMessage(any()) } just runs

        sender.sendOrThrow(1L, 1L, "m-1", "cid-1")

        verify(exactly = 1) {
            session.sendMessage(match<TextMessage> { tm ->
                tm.payload.contains("SEND_ACK") &&
                    tm.payload.contains("m-1") &&
                    tm.payload.contains("cid-1")
            })
        }
    }
}
