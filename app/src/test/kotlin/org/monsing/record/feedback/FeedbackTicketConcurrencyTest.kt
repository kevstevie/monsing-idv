package org.monsing.record.feedback

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.monsing.member.OauthProviderType
import org.monsing.member.Student
import org.monsing.member.teacher.Teacher
import org.monsing.support.TestRedisConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@Import(TestRedisConfig::class)
@DisplayName("FeedbackTicket 동시성 테스트")
class FeedbackTicketConcurrencyTest(
    @Autowired private val feedbackService: FeedbackService,
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val redisTemplate: StringRedisTemplate,
) {

    private val transactionTemplate by lazy {
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    }

    @AfterEach
    fun cleanup() {
        transactionTemplate.execute {
            entityManager.createQuery("DELETE FROM FeedbackTicket").executeUpdate()
            entityManager.createQuery("DELETE FROM FeedbackItem").executeUpdate()
            entityManager.createQuery("DELETE FROM Student").executeUpdate()
            entityManager.createQuery("DELETE FROM Teacher").executeUpdate()
        }
        val keys = redisTemplate.keys("feedback:purchase:*")
        if (keys != null && keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }

    private fun persistTeacher(id: Long): Teacher {
        val teacher = Teacher(
            id = id,
            identifier = "teacher-$id",
            oauthProviderType = OauthProviderType.GOOGLE
        )
        entityManager.persist(teacher)
        return teacher
    }

    private fun persistStudent(id: Long): Student {
        val student = Student(
            id = id,
            identifier = "student-$id",
            oauthProviderType = OauthProviderType.GOOGLE
        )
        entityManager.persist(student)
        return student
    }

    private fun persistFeedbackItem(teacher: Teacher, amount: Int): FeedbackItem {
        val item = FeedbackItem(
            teacher = teacher,
            description = "test item",
            price = 10000,
            amount = amount
        )
        entityManager.persist(item)
        return item
    }

    private fun persistFeedbackTicket(item: FeedbackItem, student: Student, amount: Int): FeedbackTicket {
        val ticket = FeedbackTicket(item, student, amount)
        entityManager.persist(ticket)
        return ticket
    }

    private fun runConcurrently(
        threadCount: Int = 2,
        action: (Int) -> Unit
    ): Pair<Int, Int> {
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) { index ->
            executor.submit {
                try {
                    readyLatch.countDown()
                    startLatch.await()
                    action(index)
                    successCount.incrementAndGet()
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        return Pair(successCount.get(), failCount.get())
    }

    private fun countTickets(): Long {
        return transactionTemplate.execute {
            entityManager
                .createQuery("SELECT COUNT(ft) FROM FeedbackTicket ft", Long::class.java)
                .singleResult
        }!!
    }

    private fun getFeedbackItemAmount(itemId: Long): Int {
        return transactionTemplate.execute {
            entityManager
                .createQuery(
                    "SELECT fi.amount FROM FeedbackItem fi WHERE fi.id = :id",
                    Int::class.javaObjectType
                )
                .setParameter("id", itemId)
                .singleResult
        }!!
    }

    @Test
    fun `기존 티켓이 없을 때 동시에 구매하면 티켓이 하나만 생성되어야 한다`() {
        val (studentId, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 10)
            entityManager.flush()
            Pair(student.id!!, item.id!!)
        }!!

        val (success, fail) = runConcurrently { feedbackService.purchaseFeedbackTicket(studentId, 1, itemId) }

        countTickets() shouldBe 1L
    }

    @Test
    fun `기존 티켓이 있을 때 동시에 증가하면 하나만 성공한다`() {
        val (studentId, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 10)
            persistFeedbackTicket(item, student, amount = 1)
            entityManager.flush()
            Pair(student.id!!, item.id!!)
        }!!

        val (success, fail) = runConcurrently { feedbackService.purchaseFeedbackTicket(studentId, 1, itemId) }

        assertSoftly {
            success shouldBe 1
            fail shouldBe 1
        }
    }

    @Test
    fun `재고 1개에 2명이 동시 구매하면 1명만 성공한다`() {
        val (studentIds, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val studentA = persistStudent(2L)
            val studentB = persistStudent(3L)
            val item = persistFeedbackItem(teacher, amount = 1)
            entityManager.flush()
            Pair(listOf(studentA.id!!, studentB.id!!), item.id!!)
        }!!

        val (success, fail) = runConcurrently { index ->
            feedbackService.purchaseFeedbackTicket(studentIds[index], 1, itemId)
        }

        assertSoftly {
            success shouldBe 1
            fail shouldBe 1
            countTickets() shouldBe 1L
            getFeedbackItemAmount(itemId) shouldBe 0
        }
    }

    @Test
    fun `재고 1개에 같은 학생이 동시 2회 구매하면 1회만 성공한다`() {
        val (studentId, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 1)
            entityManager.flush()
            Pair(student.id!!, item.id!!)
        }!!

        val (success, fail) = runConcurrently { feedbackService.purchaseFeedbackTicket(studentId, 1, itemId) }

        assertSoftly {
            success shouldBe 1
            fail shouldBe 1
            countTickets() shouldBe 1L
            getFeedbackItemAmount(itemId) shouldBe 0
        }
    }

    @Test
    fun `같은 학생이 같은 아이템을 30초 안에 두 번 구매하면 두 번째는 실패한다`() {
        val (studentId, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 10)
            entityManager.flush()
            Pair(student.id!!, item.id!!)
        }!!

        feedbackService.purchaseFeedbackTicket(studentId, 1, itemId)

        assertThrows<IllegalArgumentException> {
            feedbackService.purchaseFeedbackTicket(studentId, 1, itemId)
        }
    }

    @Test
    fun `재고 3개에 2명이 동시 구매하면 둘 다 성공하고 재고 1개가 남는다`() {
        val (studentIds, itemId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val studentA = persistStudent(2L)
            val studentB = persistStudent(3L)
            val item = persistFeedbackItem(teacher, amount = 3)
            entityManager.flush()
            Pair(listOf(studentA.id!!, studentB.id!!), item.id!!)
        }!!

        val (success, fail) = runConcurrently { index ->
            feedbackService.purchaseFeedbackTicket(studentIds[index], 1, itemId)
        }

        assertSoftly {
            success shouldBe 2
            fail shouldBe 0
            countTickets() shouldBe 2L
            getFeedbackItemAmount(itemId) shouldBe 1
        }
    }
}
