package org.monsing.service.relay

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.monsing.chat.Message

class RelayEnvelopeTest {

    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `legacy receiverId 단일 필드 envelope 도 역직렬화 가능 (롤링 배포 호환)`() {
        val legacyJson = """
            {
              "receiverId": 7,
              "message": {"id":"m-1","chatId":1,"senderId":2,"content":"hi"}
            }
        """.trimIndent()

        val envelope = objectMapper.readValue(legacyJson, RelayEnvelope::class.java)

        envelope.receiverIds shouldBe listOf(7L)
    }

    @Test
    fun `새 receiverIds 리스트 envelope 정상 역직렬화`() {
        val newJson = """
            {
              "receiverIds": [3, 4, 5],
              "message": {"id":"m-1","chatId":1,"senderId":2,"content":"hi"}
            }
        """.trimIndent()

        val envelope = objectMapper.readValue(newJson, RelayEnvelope::class.java)

        envelope.receiverIds shouldBe listOf(3L, 4L, 5L)
    }

    @Test
    fun `직렬화 round-trip - receiverIds 보존`() {
        val original = RelayEnvelope(
            receiverIds = listOf(10L, 20L),
            message = Message(id = "m-1", chatId = 1L, senderId = 2L, content = "hi")
        )

        val json = objectMapper.writeValueAsString(original)
        val decoded = objectMapper.readValue(json, RelayEnvelope::class.java)

        decoded.receiverIds shouldBe listOf(10L, 20L)
    }
}
