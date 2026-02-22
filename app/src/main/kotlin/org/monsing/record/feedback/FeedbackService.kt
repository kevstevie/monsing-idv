package org.monsing.record.feedback

import java.time.Duration
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
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
    private val feedbackReadRepository: FeedbackReadRepository,
    private val redisTemplate: StringRedisTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createFeedbackItem(teacherId: Long, price: Int, description: String, amount: Int) {
        val teacher = teacherRepository.findByIdOrElseThrow(teacherId)
        val feedbackItem = FeedbackItem(teacher, description, price, amount)
        feedbackItemRepository.save(feedbackItem)
    }

    fun getFeedbackItem(itemId: Long): FeedbackItemInfo {
        return feedbackItemRepository.findByIdOrElseThrow(itemId).toInfo()
    }

    fun getFeedbackItemsByTeacherId(teacherId: Long?, size: Int = 20, lastId: Long = 0L): List<FeedbackItemInfo> {
        return if (teacherId != null) {
            feedbackItemReadRepository.findAllByTeacherIdWithTeacher(teacherId, lastId, size)
        } else {
            feedbackItemReadRepository.findAllWithTeacher(lastId, size)
        }.map { it.toFeedbackItemInfo() }
    }

    @Transactional
    fun purchaseFeedbackTicket(studentId: Long, amount: Int, itemId: Long) {
        val idempotencyKey = "feedback:purchase:$studentId:$itemId"
        val acquired = try {
            redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", Duration.ofSeconds(IDEMPOTENCY_TTL_SECONDS))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.warn("Redis unavailable for idempotency check, proceeding without it: $idempotencyKey", e)
            null
        }
        require(acquired != false) { "중복 구매 요청입니다. 잠시 후 다시 시도해주세요." }

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    redisTemplate.delete(idempotencyKey)
                }
            }
        })

        val student = studentRepository.findByIdOrElseThrow(studentId)
        val feedbackItem = feedbackItemRepository.findByIdOrElseThrow(itemId)

        val existingTicket = feedbackTicketRepository.findByStudentAndFeedbackItem(student, feedbackItem)

        if (existingTicket != null) {
            existingTicket.increaseAmount(amount)
        } else {
            val ticket = FeedbackTicket(feedbackItem, student, amount)
            feedbackTicketRepository.save(ticket)
        }

        require(feedbackItemRepository.decreaseAmountById(itemId, amount) >= 1) {
            "판매가능한 수량을 넘었습니다: FeedbackItem"
        }
    }

    fun getFeedbackItemsByMemberId(memberId: Long, size: Int = 20, lastId: Long = 0L): List<FeedbackItemInfo> {
        val member = memberRepository.findByIdOrElseThrow(memberId)

        return when (member.memberType) {
            MemberType.TEACHER ->
                feedbackItemReadRepository.findAllByTeacherIdWithTeacher(memberId, lastId, size)

            MemberType.STUDENT ->
                feedbackItemReadRepository.findAllByStudentIdWithTeacher(memberId, lastId, size)
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

    fun findFeedbacksByMemberId(id: Long, size: Int = 20, lastId: Long = 0L): List<FeedbackDetailInfo> {
        val member = memberRepository.findByIdOrElseThrow(id)

        return when (member.memberType) {
            MemberType.STUDENT -> feedbackReadRepository.findDetailsByStudentId(id, lastId, size)
            MemberType.TEACHER -> feedbackReadRepository.findDetailsByTeacherId(id, lastId, size)
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

    companion object {
        private const val IDEMPOTENCY_TTL_SECONDS = 30L
    }
}
