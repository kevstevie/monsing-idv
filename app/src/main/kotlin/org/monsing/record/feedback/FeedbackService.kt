package org.monsing.record.feedback

import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.Student
import org.monsing.member.StudentRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.record.RecordRepository
import org.monsing.record.dto.FeedbackDetailInfo
import org.monsing.record.feedback.dto.FeedbackItemInfo
import org.monsing.teacher.dto.TeacherBrief
import org.monsing.util.findByIdOrElseThrow
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional
@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackTicketRepository: FeedbackTicketRepository,
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val feedbackItemRepository: FeedbackItemRepository,
    private val recordRepository: RecordRepository
) {

    fun createFeedbackItem(teacherId: Long, price: Int, description: String, amount: Int) {
        val teacher = teacherRepository.findByIdOrElseThrow(teacherId)
        val feedbackItem = FeedbackItem(teacher, description, price, amount)
        feedbackItemRepository.save(feedbackItem)
    }

    fun getFeedbackItem(itemId: Long): FeedbackItemInfo {
        return feedbackItemRepository.findByIdOrElseThrow(itemId).toInfo()
    }

    fun getFeedbackItemsByTeacherId(teacherId: Long?): List<FeedbackItemInfo> {
        return if (teacherId != null) {
            feedbackItemRepository.findByTeacherId(teacherId)
        } else {
            feedbackItemRepository.findAll()
        }.map { it.toInfo() }
    }

    @Transactional
    fun purchaseFeedbackTicket(studentId: Long, amount: Int, itemId: Long) {
        val student = studentRepository.findByIdOrElseThrow(studentId)
        val feedbackItem = feedbackItemRepository.findByIdOrElseThrow(itemId)

        val existingTicket = feedbackTicketRepository.findByStudentAndFeedbackItem(student, feedbackItem)

        if (existingTicket != null) {
            existingTicket.increaseAmount(amount)
        } else {
            val ticket = FeedbackTicket(feedbackItem, student, amount)
            feedbackTicketRepository.save(ticket)
        }

        feedbackItem.decreaseAmount(amount)
    }

    fun getFeedbackItemsByMemberId(memberId: Long): List<FeedbackItemInfo> {
        val member = memberRepository.findByIdOrElseThrow(memberId)

        val items = when (member.memberType) {
            MemberType.TEACHER -> {
                val teacher = teacherRepository.findByIdOrElseThrow(memberId)
                feedbackItemRepository.findByTeacher(teacher)
            }

            MemberType.STUDENT -> {
                val student = studentRepository.findByIdOrElseThrow(memberId)
                feedbackTicketRepository.findByStudent(student).map { it.feedbackItem }
            }
        }
        return items.map { it.toInfo() }
    }

    fun getRemainingTicketsMapByMemberId(memberId: Long, itemIds: List<Long>): Map<Long, Int> {
        if (itemIds.isEmpty() || !isStudent(memberId)) {
            return emptyMap()
        }

        return getRemainingTicketCountsByItemIds(memberId, itemIds)
    }

    fun isStudent(memberId: Long): Boolean {
        val member = memberRepository.findByIdOrElseThrow(memberId)
        return member.memberType == MemberType.STUDENT
    }

    fun getRemainingTicketCountsByItemIds(studentId: Long, itemIds: List<Long>): Map<Long, Int> {
        val remainingTickets = feedbackTicketRepository.findRemainingTicketCountsByStudentIdAndItemIds(
            studentId,
            itemIds
        )
        return remainingTickets.associate { it.getItemId() to it.getRemainingAmount() }
    }

    @Transactional
    fun requestFeedback(memberId: Long, recordId: Long, feedbackTicketId: Long) {
        val record = recordRepository.findByIdOrNull(recordId) ?: throw IllegalArgumentException("Record not found")
        studentRepository.findByIdOrElseThrow(memberId)
        val feedbackTicket = feedbackTicketRepository.findByIdOrElseThrow(feedbackTicketId)

        feedbackTicket.decreaseAmount(1)
        record.requestFeedback(feedbackTicket.feedbackItem.teacher)
    }

    fun findFeedbacksByMemberId(id: Long): List<FeedbackDetailInfo> {
        val member = memberRepository.findByIdOrElseThrow(id)

        return when (member.memberType) {
            MemberType.STUDENT -> {
                val student = studentRepository.findByIdOrElseThrow(id)
                recordRepository.findByStudentId(id).flatMap { record ->
                    record.feedbacks.map { it.toDetailInfo(student) }
                }
            }

            MemberType.TEACHER -> {
                val teacher = teacherRepository.findByIdOrElseThrow(id)
                val feedbacks = feedbackRepository.findByTeacher(teacher)
                feedbacks.map {
                    val student = studentRepository.findByRecordId(it.recordId)
                    it.toDetailInfo(student)
                }
            }
        }
    }

    private fun FeedbackItem.toInfo(): FeedbackItemInfo = FeedbackItemInfo(
        id = requireNotNull(id),
        teacher = TeacherBrief(
            id = requireNotNull(teacher.id),
            name = teacher.nickname.value,
            profileImageUrl = teacher.profileImage,
            verified = teacher.verified,
            description = teacher.description,
            genderType = teacher.genderType.name,
            expertiseType = teacher.expertiseType.name
        ),
        description = description,
        price = price,
        amount = amount
    )

    private fun Feedback.toDetailInfo(student: Student): FeedbackDetailInfo = FeedbackDetailInfo(
        id = requireNotNull(id),
        recordId = recordId,
        teacherId = requireNotNull(teacher.id),
        teacherName = teacher.nickname.value,
        teacherProfileImage = teacher.profileImage,
        studentId = requireNotNull(student.id),
        studentName = student.nickname.value,
        studentProfileImage = student.profileImage,
        detail = detail,
        createdAt = updatedDate
    )
}
