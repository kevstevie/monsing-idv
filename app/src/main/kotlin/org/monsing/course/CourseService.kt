package org.monsing.course

import org.monsing.course.dto.CourseInfo
import org.monsing.course.dto.LessonInfo
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.StudentRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class CourseService(
    private val courseRepository: CourseRepository,
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val lessonRepository: LessonRepository
) {

    @Transactional
    fun createCourse(
        id: Long,
        name: String,
        description: String,
        curriculum: String,
        duration: Int,
        price: Int,
        minimumLessonCount: Int,
        lessonSchedules: List<LessonSchedule>
    ) {

        val teacher = teacherRepository.findByIdOrElseThrow(id)

        val lessons = lessonSchedules.map {
            Lesson(
                lessonSchedule = it
            )
        }

        val course = Course(
            courseOverview = CourseOverview(
                name = name,
                description = description,
                curriculum = curriculum
            ),
            teacherId = requireNotNull(teacher.id),
            duration = CourseDuration(duration),
            pricePerLesson = CoursePricePerLesson(price),
            minimumLessonCount = CourseMinimumLessonCount(minimumLessonCount),
            lessons = lessons
        )

        courseRepository.save(course)
    }

    @Transactional
    fun updateCourse(
        memberId: Long,
        courseId: Long,
        name: String?,
        description: String?,
        curriculum: String?,
        duration: Int?,
        price: Int?,
        minimumLessonCount: Int?
    ) {

        val teacher = teacherRepository.findByIdOrElseThrow(memberId)

        val course = courseRepository.findByIdOrElseThrow(courseId)

        require(course.teacherId == requireNotNull(teacher.id)) {
            "Teacher is not the owner of the course"
        }

        course.update(
            name,
            description,
            curriculum,
            duration,
            price,
            minimumLessonCount
        )
    }

    @Transactional
    fun registerLesson(
        id: Long,
        courseId: Long,
        lessonId: Long,
        lessonCount: Int
    ) {
        val student = studentRepository.findByIdOrElseThrow(id)
        val course = courseRepository.findByIdOrElseThrow(courseId)

        val draftLesson = course.findLessonById(lessonId)

        val lessonsForUpdate = lessonRepository.findByTeacherAndSchedule(
            course.teacherId,
            draftLesson.lessonSchedule.dayOfWeek,
            draftLesson.lessonSchedule.startTime,
            draftLesson.lessonSchedule.startTime.plusMinutes(course.courseDuration.toLong())
        )

        check(lessonsForUpdate.none { it.isNotAvailable }) {
            "레슨을 등록할 수 없는 스케줄입니다."
        }

        lessonsForUpdate.firstOrNull { it.id == lessonId }
            ?.register(requireNotNull(student.id), lessonCount)
            ?: throw IllegalArgumentException("등록하려는 레슨이 존재하지 않습니다")

        lessonsForUpdate.filter { it.id != lessonId }.forEach { it.updateNotAvailable() }
    }

    @Transactional(readOnly = true)
    fun getCoursesByTeacherId(teacherId: Long): List<CourseInfo> {
        return courseRepository.findAllByTeacherId(teacherId).map { it.toInfo() }
    }

    @Transactional(readOnly = true)
    fun getLessonsByCourseId(id: Long): List<LessonInfo> {
        return courseRepository.findByIdOrElseThrow(id).lessons.map { it.toInfo() }
    }

    fun getLessonsWithOnAirInfoByMemberId(id: Long): List<LessonInfo> {
        val member = memberRepository.findByIdOrElseThrow(id)

        if (member.memberType == MemberType.TEACHER) {
            return courseRepository.findAllByTeacherId(id)
                .flatMap { it.lessons }
                .filter { it.lessonStatusType == LessonStatusType.RESERVED }
                .map { it.toInfo(isOnAir = it.classRoomStatusType == ClassRoomStatusType.OPEN) }
        }

        return lessonRepository.findAllByStudentId(id)
            .map { it.toInfo(isOnAir = it.classRoomStatusType == ClassRoomStatusType.OPEN) }
    }

    private fun Course.toInfo(): CourseInfo = CourseInfo(
        id = requireNotNull(id),
        teacherId = teacherId,
        name = courseOverview.name,
        description = courseOverview.description,
        curriculum = courseOverview.curriculum,
        duration = duration.value,
        price = pricePerLesson.value,
        minimumLessonCount = minimumLessonCount.value
    )

    private fun Lesson.toInfo(isOnAir: Boolean = false): LessonInfo = LessonInfo(
        id = requireNotNull(id),
        dayOfWeek = lessonSchedule.dayOfWeek.name,
        startTime = lessonSchedule.startTime.toString(),
        isAvailable = lessonStatusType.isAvailable(),
        isOnAir = isOnAir
    )
}
