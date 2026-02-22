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
import org.monsing.member.OauthProviderType
import org.monsing.member.Student
import org.monsing.member.teacher.Teacher
import org.monsing.record.Record
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@DisplayName("RequestFeedback 동시성 테스트")
class RequestFeedbackConcurrencyTest(
    @Autowired private val feedbackService: FeedbackService,
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val entityManager: EntityManager
) {

    private val transactionTemplate by lazy {
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    }

    @AfterEach
    fun cleanup() {
        transactionTemplate.execute {
            entityManager.createQuery("DELETE FROM Feedback").executeUpdate()
            entityManager.createQuery("DELETE FROM FeedbackTicket").executeUpdate()
            entityManager.createQuery("DELETE FROM FeedbackItem").executeUpdate()
            entityManager.createQuery("DELETE FROM Record").executeUpdate()
            entityManager.createQuery("DELETE FROM Student").executeUpdate()
            entityManager.createQuery("DELETE FROM Teacher").executeUpdate()
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

    private fun persistRecord(studentId: Long): Record {
        val record = Record(
            title = "test-record",
            studentId = studentId,
            fileKey = "test-key",
            url = "https://test.com/record"
        )
        entityManager.persist(record)
        return record
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

    private fun countFeedbacks(recordId: Long): Long {
        return transactionTemplate.execute {
            entityManager
                .createQuery(
                    "SELECT COUNT(f) FROM Feedback f WHERE f.record.id = :recordId",
                    Long::class.java
                )
                .setParameter("recordId", recordId)
                .singleResult
        }!!
    }

    private fun getTicketAmount(ticketId: Long): Int {
        return transactionTemplate.execute {
            entityManager
                .createQuery(
                    "SELECT ft._amount FROM FeedbackTicket ft WHERE ft.id = :id",
                    Int::class.javaObjectType
                )
                .setParameter("id", ticketId)
                .singleResult
        }!!
    }

    @Test
    fun `같은 티켓으로 동시 2회 요청하면 1회만 성공한다`() {
        val (studentId, recordId, ticketId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 10)
            val ticket = persistFeedbackTicket(item, student, amount = 2)
            val record = persistRecord(student.id!!)
            entityManager.flush()
            Triple(student.id!!, record.id!!, ticket.id!!)
        }!!

        val (success, fail) = runConcurrently {
            feedbackService.requestFeedback(studentId, recordId, ticketId)
        }

        assertSoftly {
            success shouldBe 1
            fail shouldBe 1
            countFeedbacks(recordId) shouldBe 1L
            getTicketAmount(ticketId) shouldBe 1
        }
    }

    @Test
    fun `티켓 잔여량 1개로 동시 2회 요청하면 1회만 성공하고 잔여량 0이다`() {
        val (studentId, recordId, ticketId) = transactionTemplate.execute {
            val teacher = persistTeacher(1L)
            val student = persistStudent(2L)
            val item = persistFeedbackItem(teacher, amount = 10)
            val ticket = persistFeedbackTicket(item, student, amount = 1)
            val record = persistRecord(student.id!!)
            entityManager.flush()
            Triple(student.id!!, record.id!!, ticket.id!!)
        }!!

        val (success, fail) = runConcurrently {
            feedbackService.requestFeedback(studentId, recordId, ticketId)
        }

        assertSoftly {
            success shouldBe 1
            fail shouldBe 1
            countFeedbacks(recordId) shouldBe 1L
            getTicketAmount(ticketId) shouldBe 0
        }
    }

    @Test
    fun `같은 record에 다른 teacher 티켓으로 동시 요청하면 둘 다 성공한다`() {
        val (studentIds, recordId, ticketIds) = transactionTemplate.execute {
            val teacherA = persistTeacher(1L)
            val teacherB = persistTeacher(10L)
            val studentA = persistStudent(2L)
            val studentB = persistStudent(3L)
            val itemA = persistFeedbackItem(teacherA, amount = 10)
            val itemB = persistFeedbackItem(teacherB, amount = 10)
            val ticketA = persistFeedbackTicket(itemA, studentA, amount = 5)
            val ticketB = persistFeedbackTicket(itemB, studentB, amount = 5)
            val record = persistRecord(studentA.id!!)
            entityManager.flush()
            Triple(
                listOf(studentA.id!!, studentB.id!!),
                record.id!!,
                listOf(ticketA.id!!, ticketB.id!!)
            )
        }!!

        val (success, fail) = runConcurrently { index ->
            feedbackService.requestFeedback(studentIds[index], recordId, ticketIds[index])
        }

        assertSoftly {
            success shouldBe 2
            fail shouldBe 0
            countFeedbacks(recordId) shouldBe 2L
        }
    }
}
