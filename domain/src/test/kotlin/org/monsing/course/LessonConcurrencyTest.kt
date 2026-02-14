package org.monsing.course

import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.monsing.TestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@DataJpaTest
@ContextConfiguration(classes = [TestContext::class])
@EnableJpaRepositories(basePackageClasses = [LessonRepository::class, CourseRepository::class])
@EntityScan(basePackageClasses = [Course::class, Lesson::class])
@DisplayName("PESSIMISTIC_WRITE 동시성 테스트")
class LessonConcurrencyTest(
    @Autowired private val lessonRepository: LessonRepository,
    @Autowired private val courseRepository: CourseRepository,
    @Autowired private val entityManagerFactory: EntityManagerFactory
) {

    private val transactionManager by lazy { JpaTransactionManager(entityManagerFactory) }
    private val transactionTemplate by lazy {
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    }
    private val teacherId = 1L

    @AfterEach
    fun cleanup() {
        transactionTemplate.execute {
            lessonRepository.deleteAll()
            courseRepository.deleteAll()
        }
    }

    private fun createCourseInNewTx(
        teacherId: Long,
        duration: Int,
        lessons: List<Lesson>
    ): Course = transactionTemplate.execute {
        courseRepository.save(
            Course(
                courseOverview = CourseOverview(
                    name = "test",
                    description = "test",
                    curriculum = "test"
                ),
                teacherId = teacherId,
                duration = CourseDuration(duration),
                pricePerLesson = CoursePricePerLesson(10000),
                minimumLessonCount = CourseMinimumLessonCount(1),
                lessons = lessons
            )
        )
    }!!

    @Test
    fun `동시에 같은 레슨을 등록하면 하나만 성공한다`() {
        val course = createCourseInNewTx(
            teacherId,
            60,
            listOf(
                Lesson(
                    lessonSchedule = LessonSchedule(
                        dayOfWeek = DayOfWeek.MON,
                        startTime = LocalTime.of(10, 0)
                    )
                )
            )
        )
        val lessonId = course.lessons[0].id!!

        val threadCount = 2
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
                    transactionTemplate.execute {
                        val lessons = lessonRepository.findByTeacherAndSchedule(
                            teacherId = teacherId,
                            dayOfWeek = DayOfWeek.MON,
                            startTime = LocalTime.of(9, 0),
                            endTime = LocalTime.of(11, 0)
                        )
                        val target = lessons.first { it.id == lessonId }
                        check(target.lessonStatusType.isAvailable()) {
                            "Lesson is not available"
                        }
                        target.register(index.toLong() + 100, 5)
                    }
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

        (successCount.get() + failCount.get()) shouldBe threadCount
        successCount.get() shouldBe 1
        failCount.get() shouldBe 1
    }
}
