package org.monsing.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.Message
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class MessageBufferTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var messageIdStrategy: MessageIdStrategy
    private lateinit var messageBuffer: MessageBuffer

    @BeforeEach
    fun setUp() {
        messageRepository = mockk(relaxed = true)
        messageIdStrategy = mockk(relaxed = true)
        messageBuffer = MessageBuffer(messageRepository, messageIdStrategy)
    }

    @Test
    fun `handle - 이벤트 수신 시 ID 생성 후 버퍼에 추가`() {
        val msg = Message(chatId = "chat-1", senderId = 1L, content = "hello")

        messageBuffer.handle(MessageCreatedEvent(msg))

        verify(exactly = 1) { messageIdStrategy.generateId(msg) }
    }

    @Test
    fun `flush - 버퍼에 메시지가 있으면 saveAll 호출`() {
        val msg1 = Message(chatId = "chat-1", senderId = 1L, content = "hello")
        val msg2 = Message(chatId = "chat-1", senderId = 2L, content = "world")

        messageBuffer.handle(MessageCreatedEvent(msg1))
        messageBuffer.handle(MessageCreatedEvent(msg2))
        messageBuffer.flush()

        verify(exactly = 1) { messageRepository.saveAll(listOf(msg1, msg2)) }
    }

    @Test
    fun `flush - 빈 버퍼면 saveAll 미호출`() {
        messageBuffer.flush()

        verify(exactly = 0) { messageRepository.saveAll(any()) }
    }

    @Test
    fun `flush - saveAll 실패해도 예외 전파되지 않음`() {
        every { messageRepository.saveAll(any()) } throws RuntimeException("DB error")

        messageBuffer.handle(MessageCreatedEvent(Message(chatId = "chat-1", senderId = 1L, content = "test")))
        messageBuffer.flush()

        verify(exactly = 1) { messageRepository.saveAll(any()) }
    }

    @Test
    fun `handle - 동시성 환경에서 메시지 유실 없이 버퍼에 추가`() {
        val savedBatches = mutableListOf<List<Message>>()
        every { messageRepository.saveAll(capture(savedBatches)) } just runs

        val threadCount = 10
        val messagesPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadIdx ->
            executor.submit {
                repeat(messagesPerThread) { msgIdx ->
                    messageBuffer.handle(
                        MessageCreatedEvent(
                            Message(
                                chatId = "chat-$threadIdx",
                                senderId = threadIdx.toLong(),
                                content = "msg-$msgIdx"
                            )
                        )
                    )
                }
                latch.countDown()
            }
        }

        latch.await()
        messageBuffer.flush()

        val totalExpected = threadCount * messagesPerThread
        savedBatches.sumOf { it.size } shouldBe totalExpected

        executor.shutdown()
    }
}
