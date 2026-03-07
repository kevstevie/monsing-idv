package org.monsing.record.feedback

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class IdempotencyRollbackListener(
    private val redisTemplate: StringRedisTemplate,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    fun onRollback(event: IdempotencyRollbackEvent) {
        redisTemplate.delete(event.key)
    }
}
