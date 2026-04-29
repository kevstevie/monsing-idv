package org.monsing.alert

import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SessionCallback
import org.springframework.stereotype.Repository

@Repository
class FcmTokenRepository(
    private val fcmTokenRedisTemplate: RedisTemplate<String, String>
) {

    fun saveToken(memberId: Long, token: String) {
        fcmTokenRedisTemplate.opsForSet().add(tokenKey(memberId), token)
    }

    fun deleteToken(memberId: Long, token: String) {
        fcmTokenRedisTemplate.opsForSet().remove(tokenKey(memberId), token)
    }

    fun findTokens(memberIds: Collection<Long>): Map<Long, Set<String>> {
        val ids = memberIds.distinct()
        if (ids.isEmpty()) return emptyMap()

        val results = fcmTokenRedisTemplate.executePipelined(sMembersPipeline(ids))

        @Suppress("UNCHECKED_CAST")
        return ids.zip(results).associate { (id, raw) ->
            id to (raw as? Set<String> ?: emptySet())
        }
    }

    private fun sMembersPipeline(ids: List<Long>) = object : SessionCallback<Any?> {
        override fun <K : Any, V : Any> execute(operations: RedisOperations<K, V>): Any? {
            @Suppress("UNCHECKED_CAST")
            val ops = operations as RedisOperations<String, String>
            ids.forEach { ops.opsForSet().members(tokenKey(it)) }
            return null
        }
    }

    private fun tokenKey(memberId: Long) = "$TOKEN_PREFIX$memberId"

    companion object {
        private const val TOKEN_PREFIX = "TOKEN:"
    }
}
