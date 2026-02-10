package org.monsing.service

import java.util.concurrent.ConcurrentLinkedQueue
import org.monsing.chat.Message
import org.monsing.chat.MessageIdStrategy
import org.monsing.chat.MessageRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MessageBuffer(
    private val messageRepository: MessageRepository,
    private val messageIdStrategy: MessageIdStrategy
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ConcurrentLinkedQueue<Message>()

    @EventListener
    fun handle(event: MessageCreatedEvent) {
        messageIdStrategy.generateId(event.message)
        buffer.add(event.message)
    }

    @Scheduled(fixedDelay = 500)
    fun flush() {
        val messages = drainBuffer()
        if (messages.isEmpty()) return

        messages.chunked(MAX_BATCH_SIZE).forEach { batch ->
            saveBatch(batch)
        }
    }

    private fun drainBuffer(): List<Message> {
        return generateSequence { buffer.poll() }.toList()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun saveBatch(batch: List<Message>) {
        try {
            messageRepository.saveAll(batch)
            log.debug("Message batch saved: size={}", batch.size)
        } catch (e: Exception) {
            log.error("Failed to save message batch (size={})", batch.size, e)
        }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 200
    }
}
