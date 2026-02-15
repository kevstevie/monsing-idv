package org.monsing.course

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.monsing.BaseEntity

@Entity
@Table(indexes = [Index(name = "course_id_schedule_idx", columnList = "course_id, day_of_week, start_time")])
class Lesson(

    id: Long? = null,

    @Embedded
    val lessonSchedule: LessonSchedule,

    var studentId: Long? = null,

    var lessonRemaining: Int? = null,

    @Column(nullable = false)
    var lessonStatusType: LessonStatusType = LessonStatusType.AVAILABLE,

    @Column(nullable = false)
    var classRoomStatusType: ClassRoomStatusType = ClassRoomStatusType.CLOSED
) : BaseEntity(id = id) {

    val existsRemainingLessonCount
        get() = (lessonRemaining ?: 0) > 0

    val isNotAvailable
        get() = lessonStatusType != LessonStatusType.AVAILABLE

    fun register(studentId: Long, lessonCount: Int) {
        check(lessonStatusType.isAvailable()) {
            "Lesson is not available"
        }

        this.studentId = studentId
        lessonRemaining = lessonCount
        lessonStatusType = LessonStatusType.RESERVED
    }

    fun updateNotAvailable() {
        lessonStatusType = LessonStatusType.NOT_AVAILABLE
    }

    fun overlappingWith(lesson: Lesson, duration: Int) {
        if (lessonSchedule.dayOfWeek == lesson.lessonSchedule.dayOfWeek &&
            lessonSchedule.startTime >= lesson.lessonSchedule.startTime &&
            lessonSchedule.startTime < lesson.lessonSchedule.startTime.plusMinutes(duration.toLong())
        ) {
            if (lessonStatusType.isAvailable()) {
                lessonStatusType = LessonStatusType.NOT_AVAILABLE
            }
        }
    }

    fun openClassRoom() {
        classRoomStatusType = ClassRoomStatusType.OPEN
    }

    fun completeClassRoom() {
        check(classRoomStatusType == ClassRoomStatusType.OPEN) {
            "ClassRoom is not opened"
        }
        classRoomStatusType = ClassRoomStatusType.CLOSED
        reduceRemainingCount()
    }

    private fun reduceRemainingCount() {
        check(lessonStatusType == LessonStatusType.RESERVED) {
            "Lesson is not reserved"
        }
        check(requireNotNull(lessonRemaining) > 0) {
            "Lesson count is lesser than 0"
        }
        lessonRemaining = lessonRemaining?.minus(1)
    }
}
