package org.monsing.service.relay

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.springframework.data.redis.core.StringRedisTemplate

class RedisChatRelayPublisherTest {

    private val template = mockk<StringRedisTemplate>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val publisher = RedisChatRelayPublisher(template, objectMapper)
    private val message = Message(id = "m-1", chatId = 1L, senderId = 2L, content = "hi")

    @Test
    fun `publishRelayBatch - 빈 리스트는 no-op`() {
        publisher.publishRelayBatch(emptyList(), message) shouldBe true

        verify(exactly = 0) { template.convertAndSend(any(), any<String>()) }
    }

    @Test
    fun `publishRelayBatch - 정상 publish`() {
        publisher.publishRelayBatch(listOf(2L, 3L), message) shouldBe true

        verify(exactly = 1) { template.convertAndSend(RedisChatRelayPublisher.BROADCAST_CHANNEL, any<String>()) }
    }

    @Test
    fun `publishRelayBatch - cap 초과 시 IllegalArgumentException`() {
        val oversized = (1L..(RedisChatRelayPublisher.MAX_RELAY_BATCH + 1L)).toList()

        assertThrows(IllegalArgumentException::class.java) {
            publisher.publishRelayBatch(oversized, message)
        }
        verify(exactly = 0) { template.convertAndSend(any(), any<String>()) }
    }

    @Test
    fun `publishRelayBatch - Redis 예외 시 false 반환`() {
        every { template.convertAndSend(any(), any<String>()) } throws RuntimeException("boom")

        publisher.publishRelayBatch(listOf(2L), message) shouldBe false
    }
}
