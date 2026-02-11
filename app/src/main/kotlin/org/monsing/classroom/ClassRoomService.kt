package org.monsing.classroom

import org.monsing.auth.jwt.LiveKitTokenManager
import org.monsing.course.ClassRoomStatusType
import org.monsing.course.LessonRepository
import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.StudentRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ClassRoomService(
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val lessonRepository: LessonRepository,
    private val liveKitTokenManager: LiveKitTokenManager,
) {

    @Transactional
    fun completeClassRoom(teacherId: Long, lessonId: Long) {
        val lesson = lessonRepository.findByIdOrElseThrow(lessonId)
        teacherRepository.findByIdOrElseThrow(teacherId)
        require(lessonRepository.existsByTeacherIdAndLessonId(teacherId, lessonId)) {
            "lesson is not matched"
        }

        lesson.completeClassRoom()
    }

    @Transactional
    fun enterClassRoom(memberId: Long, lessonId: Long): String {
        val lesson = lessonRepository.findByIdOrElseThrow(lessonId)
        val member = memberRepository.findByIdOrElseThrow(memberId)

        val nickname = when (member.memberType) {
            MemberType.TEACHER -> {
                val teacher = teacherRepository.findByIdOrElseThrow(memberId)
                require(lessonRepository.existsByTeacherIdAndLessonId(memberId, lessonId)) {
                    "Teacher is not matched"
                }
                lesson.openClassRoom()
                teacher.nickname.value
            }
            MemberType.STUDENT -> {
                val student = studentRepository.findByIdOrElseThrow(memberId)
                require(lesson.studentId == memberId) {
                    "Student is not matched"
                }
                check(lesson.classRoomStatusType == ClassRoomStatusType.OPEN) {
                    "ClassRoom is not opened"
                }
                check(lesson.existsRemainingLessonCount) {
                    "Lesson is not available"
                }
                student.nickname.value
            }
        }

        return liveKitTokenManager.generateToken(nickname, lessonId.toString(), memberId.toString())
    }
}
