package org.monsing.chat

enum class MessageStatus {
    PENDING,
    RELAY_PENDING,
    COMPLETE,
    FAILED,
    NOTIFIED,
    DEAD_LETTERED;

    fun canTransitionTo(target: MessageStatus): Boolean = target in ALLOWED[this].orEmpty()

    companion object {
        private val ALLOWED: Map<MessageStatus, Set<MessageStatus>> = mapOf(
            PENDING to setOf(RELAY_PENDING, COMPLETE, FAILED),
            RELAY_PENDING to setOf(COMPLETE, FAILED),
            FAILED to setOf(NOTIFIED, DEAD_LETTERED),
            COMPLETE to emptySet(),
            NOTIFIED to emptySet(),
            DEAD_LETTERED to emptySet()
        )
    }
}
