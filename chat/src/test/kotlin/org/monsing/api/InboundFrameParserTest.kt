package org.monsing.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.monsing.service.MessageDto

class InboundFrameParserTest {

    private val parser = InboundFrameParser(createObjectMapper())

    @Test
    fun `CHAT 타입 페이로드는 Chat 프레임으로 파싱된다`() {
        val frame = parser.parse("""{"type":"CHAT","chatId":1,"content":"hello"}""")

        frame.shouldBeInstanceOf<InboundFrame.Chat>()
        frame.message shouldBe MessageDto(chatId = 1L, content = "hello")
    }

    @Test
    fun `type 필드가 없으면 CHAT 프레임으로 파싱된다`() {
        val frame = parser.parse("""{"chatId":1,"content":"hello"}""")

        frame.shouldBeInstanceOf<InboundFrame.Chat>()
        frame.message shouldBe MessageDto(chatId = 1L, content = "hello")
    }

    @Test
    fun `clientMessageId 가 포함된 CHAT 프레임을 파싱한다`() {
        val frame = parser.parse("""{"type":"CHAT","chatId":1,"content":"hi","clientMessageId":"c-1"}""")

        frame.shouldBeInstanceOf<InboundFrame.Chat>()
        frame.message.clientMessageId shouldBe "c-1"
    }

    @Test
    fun `ACK 타입 페이로드는 Ack 프레임으로 파싱된다`() {
        val frame = parser.parse("""{"type":"ACK","messageId":"msg-1"}""")

        frame.shouldBeInstanceOf<InboundFrame.Ack>()
        frame.messageId shouldBe "msg-1"
    }

    @Test
    fun `ACK 프레임에 messageId 가 없으면 null 을 반환한다`() {
        val frame = parser.parse("""{"type":"ACK"}""")

        frame shouldBe null
    }

    @Test
    fun `알 수 없는 type 은 CHAT 프레임으로 파싱된다`() {
        val frame = parser.parse("""{"type":"UNKNOWN","chatId":1,"content":"hello"}""")

        frame.shouldBeInstanceOf<InboundFrame.Chat>()
    }

    @Test
    fun `서버 전용 SEND_ACK 프레임은 null 을 반환한다`() {
        val frame = parser.parse("""{"type":"SEND_ACK","messageId":"m"}""")

        frame shouldBe null
    }

    private fun createObjectMapper(): ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
}
