package org.monsing.course

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.monsing.TestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import java.time.LocalTime

@DataJpaTest
@ContextConfiguration(classes = [TestContext::class])
@EnableJpaRepositories(basePackageClasses = [LessonRepository::class, CourseRepository::class])
@EntityScan(basePackageClasses = [Course::class, Lesson::class])
class LessonRepositoryTest(
    @Autowired private val lessonRepository: LessonRepository,
    @Autowired private val courseRepository: CourseRepository
) {

    private val teacherId = 1L

    private fun createCourse(
        teacherId: Long,
        duration: Int,
        lessons: List<Lesson>
    ): Course = courseRepository.save(
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

    private fun createLesson(
        dayOfWeek: DayOfWeek,
        hour: Int,
        minute: Int = 0
    ): Lesson = Lesson(
        lessonSchedule = LessonSchedule(
            dayOfWeek = dayOfWeek,
            startTime = LocalTime.of(hour, minute)
        )
    )

    @Nested
    @DisplayName("findByTeacherAndSchedule JPQL 정합성")
    inner class JpqlCorrectnessTest {

        @Test
        fun `같은 교사, 같은 요일, 시간 범위 내 레슨을 반환한다`() {
            val lesson = createLesson(DayOfWeek.MON, 10, 0)
            createCourse(teacherId, 60, listOf(lesson))

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 1
            result[0].lessonSchedule.dayOfWeek shouldBe DayOfWeek.MON
            result[0].lessonSchedule.startTime shouldBe LocalTime.of(10, 0)
        }

        @Test
        fun `다른 요일의 레슨은 반환하지 않는다`() {
            val mondayLesson = createLesson(DayOfWeek.MON, 10, 0)
            val tuesdayLesson = createLesson(DayOfWeek.TUE, 10, 0)
            createCourse(teacherId, 60, listOf(mondayLesson, tuesdayLesson))

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 1
            result[0].lessonSchedule.dayOfWeek shouldBe DayOfWeek.MON
        }

        @Test
        fun `시간 범위 밖의 레슨은 반환하지 않는다`() {
            val inRange = createLesson(DayOfWeek.MON, 10, 0)
            val outOfRange = createLesson(DayOfWeek.MON, 15, 0)
            createCourse(teacherId, 60, listOf(inRange, outOfRange))

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 1
            result[0].lessonSchedule.startTime shouldBe LocalTime.of(10, 0)
        }

        @Test
        fun `다른 교사의 레슨은 반환하지 않는다`() {
            val otherTeacherId = 2L
            createCourse(
                teacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 10, 0))
            )
            createCourse(
                otherTeacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 10, 0))
            )

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 1
        }

        @Test
        fun `endTime 경계값 - startTime이 endTime과 같으면 반환하지 않는다`() {
            createCourse(
                teacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 11, 0))
            )

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 0
        }

        @Test
        fun `startTime 경계값 - startTime이 정확히 같으면 반환한다`() {
            createCourse(
                teacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 10, 0))
            )

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 1
        }

        @Test
        fun `여러 과목에 걸친 같은 교사의 레슨을 모두 반환한다`() {
            createCourse(
                teacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 10, 0))
            )
            createCourse(
                teacherId,
                60,
                listOf(createLesson(DayOfWeek.MON, 10, 30))
            )

            val result = lessonRepository.findByTeacherAndSchedule(
                teacherId = teacherId,
                dayOfWeek = DayOfWeek.MON,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0)
            )

            result shouldHaveSize 2
            result.map { it.lessonSchedule.startTime }
                .shouldContainExactlyInAnyOrder(
                    LocalTime.of(10, 0),
                    LocalTime.of(10, 30)
                )
        }
    }

}
