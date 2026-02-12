package org.monsing.record.feedback

import org.monsing.member.MemberRepository
import org.monsing.member.MemberType
import org.monsing.member.StudentRepository
import org.monsing.member.teacher.TeacherRepository
import org.monsing.record.RecordRepository
import org.monsing.record.dto.FeedbackDetailInfo
import org.monsing.record.feedback.dto.FeedbackItemInfo
import org.monsing.record.feedback.projection.FeedbackDetailReadProjection
import org.monsing.record.feedback.projection.FeedbackItemReadProjection
import org.monsing.teacher.dto.TeacherBrief
import org.monsing.util.findByIdOrElseThrow
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional
@Service
class FeedbackService(
    private val feedbackTicketRepository: FeedbackTicketRepository,
    private val memberRepository: MemberRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository,
    private val feedbackItemRepository: FeedbackItemRepository,
    private val recordRepository: RecordRepository,
    private val feedbackItemReadRepository: FeedbackItemReadRepository,
    private val feedbackReadRepository: FeedbackReadRepository
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
            feedbackItemReadRepository.findAllByTeacherIdWithTeacher(teacherId)
        } else {
            feedbackItemReadRepository.findAllWithTeacher()
        }.map { it.toFeedbackItemInfo() }
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

        return when (member.memberType) {
            MemberType.TEACHER ->
                feedbackItemReadRepository.findAllByTeacherIdWithTeacher(memberId)
            MemberType.STUDENT ->
                feedbackItemReadRepository.findAllByStudentIdWithTeacher(memberId)
        }.map { it.toFeedbackItemInfo() }
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
            MemberType.STUDENT -> feedbackReadRepository.findDetailsByStudentId(id)
            MemberType.TEACHER -> feedbackReadRepository.findDetailsByTeacherId(id)
        }.map { it.toFeedbackDetailInfo() }
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

    private fun FeedbackItemReadProjection.toFeedbackItemInfo(): FeedbackItemInfo = FeedbackItemInfo(
        id = getId(),
        teacher = TeacherBrief(
            id = getTeacherId(),
            name = getTeacherNickname(),
            profileImageUrl = getTeacherProfileImage(),
            verified = getTeacherVerified(),
            description = getTeacherDescription(),
            genderType = getTeacherGenderType(),
            expertiseType = getTeacherExpertiseType()
        ),
        description = getDescription(),
        price = getPrice(),
        amount = getAmount()
    )

    private fun FeedbackDetailReadProjection.toFeedbackDetailInfo(): FeedbackDetailInfo = FeedbackDetailInfo(
        id = getId(),
        recordId = getRecordId(),
        teacherId = getTeacherId(),
        teacherName = getTeacherNickname(),
        teacherProfileImage = getTeacherProfileImage(),
        studentId = getStudentId(),
        studentName = getStudentNickname(),
        studentProfileImage = getStudentProfileImage(),
        detail = getDetail(),
        createdAt = getUpdatedDate()
    )
}
