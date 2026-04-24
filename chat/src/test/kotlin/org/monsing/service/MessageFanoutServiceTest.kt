package org.monsing.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.monsing.chat.MemberChatRepository
import org.monsing.chat.MessageDelivery
import org.monsing.chat.MessageDeliveryRepository

class MessageFanoutServiceTest {

    private lateinit var memberChatRepository: MemberChatRepository
    private lateinit var messageDeliveryRepository: MessageDeliveryRepository
    private lateinit var service: MessageFanoutService

    @BeforeEach
    fun setUp() {
        memberChatRepository = mockk(relaxed = true)
        messageDeliveryRepository = mockk(relaxed = true)
        service = MessageFanoutService(memberChatRepository, messageDeliveryRepository)
    }

    @Test
    fun `persistDeliveries - 수신자마다 MessageDelivery를 PENDING 상태로 저장하고 수신자 리스트를 반환`() {
        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns listOf(2L, 3L)

        val result = service.persistDeliveries(1L, 1L, "m-1")

        assertEquals(listOf(2L, 3L), result)
        verify(exactly = 1) {
            messageDeliveryRepository.saveAll(match<Iterable<MessageDelivery>> { deliveries ->
                val list = deliveries.toList()
                list.size == 2 &&
                    list.all { it.messageId == "m-1" } &&
                    list.map { it.receiverId }.toSet() == setOf(2L, 3L)
            })
        }
    }

    @Test
    fun `persistDeliveries - 수신자가 없으면 저장하지 않고 빈 리스트 반환`() {
        every { memberChatRepository.findReceiverIdByChatId(1L, 1L) } returns emptyList()

        val result = service.persistDeliveries(1L, 1L, "m-1")

        assertEquals(emptyList<Long>(), result)
        verify(exactly = 0) { messageDeliveryRepository.saveAll(any<Iterable<MessageDelivery>>()) }
    }
}
