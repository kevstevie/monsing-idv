package org.monsing.alert

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import redis.embedded.RedisServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FcmTokenRepositoryTest {

    private lateinit var redisServer: RedisServer
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var template: RedisTemplate<String, String>
    private lateinit var repository: FcmTokenRepository

    @BeforeAll
    fun startRedis() {
        redisServer = RedisServer(REDIS_PORT)
        redisServer.start()
        connectionFactory = LettuceConnectionFactory("localhost", REDIS_PORT).apply {
            afterPropertiesSet()
        }
        template = RedisTemplate<String, String>().apply {
            connectionFactory = this@FcmTokenRepositoryTest.connectionFactory
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
        repository = FcmTokenRepository(template)
    }

    @AfterAll
    fun stopRedis() {
        connectionFactory.destroy()
        redisServer.stop()
    }

    @BeforeEach
    fun cleanUp() {
        template.connectionFactory!!.connection.use { it.serverCommands().flushDb() }
    }

    @Test
    fun `saveToken + findTokens - 단일 사용자 다중 토큰 저장 조회`() {
        repository.saveToken(1L, "phone")
        repository.saveToken(1L, "tablet")

        repository.findTokens(listOf(1L))[1L] shouldContainExactlyInAnyOrder setOf("phone", "tablet")
    }

    @Test
    fun `deleteToken - 특정 토큰만 제거`() {
        repository.saveToken(1L, "phone")
        repository.saveToken(1L, "tablet")

        repository.deleteToken(1L, "phone")

        repository.findTokens(listOf(1L))[1L] shouldBe setOf("tablet")
    }

    @Test
    fun `findTokens - 여러 receiver 한 번의 pipelined 왕복으로 조회`() {
        repository.saveToken(1L, "phone-1")
        repository.saveToken(2L, "phone-2")
        repository.saveToken(2L, "tablet-2")
        repository.saveToken(3L, "phone-3")

        val result = repository.findTokens(listOf(1L, 2L, 3L))

        result[1L] shouldBe setOf("phone-1")
        result[2L] shouldContainExactlyInAnyOrder setOf("phone-2", "tablet-2")
        result[3L] shouldBe setOf("phone-3")
    }

    @Test
    fun `findTokens - 토큰 미등록 receiver 는 빈 Set 으로 매핑`() {
        repository.saveToken(1L, "phone")

        val result = repository.findTokens(listOf(1L, 999L))

        result[1L] shouldBe setOf("phone")
        result[999L] shouldBe emptySet()
    }

    @Test
    fun `findTokens - 빈 리스트 입력 시 빈 맵 반환 Redis 호출 없음`() {
        val result = repository.findTokens(emptyList())

        result shouldContainExactly emptyMap()
    }

    @Test
    fun `findTokens - 중복 receiverId 는 distinct 처리하여 한 번만 조회`() {
        repository.saveToken(1L, "phone")

        val result = repository.findTokens(listOf(1L, 1L, 1L))

        result.size shouldBe 1
        result[1L] shouldBe setOf("phone")
    }

    @Test
    fun `findTokens - 대량 receiver (100명) 단일 pipelined 호출`() {
        (1L..100L).forEach { repository.saveToken(it, "tok-$it") }

        val result = repository.findTokens((1L..100L).toList())

        result.size shouldBe 100
        result[1L] shouldBe setOf("tok-1")
        result[50L] shouldBe setOf("tok-50")
        result[100L] shouldBe setOf("tok-100")
    }

    companion object {
        private const val REDIS_PORT = 16_380
    }
}
