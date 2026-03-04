package org.monsing.record

import org.monsing.member.StudentRepository
import org.monsing.record.dto.FeedbackInfo
import org.monsing.record.dto.RecordInfo
import org.monsing.record.feedback.FeedbackTicketRepository
import org.monsing.record.feedback.dto.FeedbackTicketInfo
import org.monsing.record.projection.FeedbackInfoProjection
import org.monsing.util.findByIdOrElseThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional
@Service
class RecordService(
    private val recordRepository: RecordRepository,
    private val feedbackTicketRepository: FeedbackTicketRepository,
    private val studentRepository: StudentRepository,
    private val recordReadRepository: RecordReadRepository
) {

    @Transactional
    fun saveRecord(record: Record): Record {
        return recordRepository.save(record)
    }

    @Transactional
    fun writeFeedback(writerId: Long, recordId: Long, detail: String) {
        val record = recordRepository.findByIdOrElseThrow(recordId)
        val feedback = record.feedbacks.find { it.teacher.id == writerId }
            ?: throw IllegalArgumentException("Feedback not found")

        feedback.writeFeedback(detail)
    }

    @Transactional(readOnly = true)
    fun findRecordsByStudentId(studentId: Long, size: Int?, lastId: Long?): List<RecordInfo> {
        return recordReadRepository.findRecordsByStudentIdWithPaging(studentId, size ?: 20, lastId ?: 0)
            .map { it.toInfo() }
    }

    @Transactional(readOnly = true)
    fun findRecordById(recordId: Long, memberId: Long): RecordInfo {
        val record = recordRepository.findByIdOrElseThrow(recordId)

        require(record.isOwnedBy(memberId)) { "Record does not belong to member" }

        val feedbacks = recordReadRepository.findFeedbacksByRecordId(recordId)
            .map { it.toFeedbackInfo() }

        return RecordInfo(
            id = requireNotNull(record.id),
            url = record.url,
            createdAt = record.createdDate,
            feedbacks = feedbacks
        )
    }

    @Transactional
    fun deleteRecord(recordId: Long, id: Long) {
        val record = recordRepository.findByIdOrElseThrow(recordId)
        val student = studentRepository.findByIdOrElseThrow(id)
        require(record.studentId == student.id) { "Record does not belong to student" }
        record.notCompletedFeedBacks.forEach {
            feedbackTicketRepository.findByStudent(student).forEach { ticket ->
                if (ticket.feedbackItem.id == it.id) {
                    ticket.increaseAmount()
                }
            }
        }
        recordRepository.delete(record)
    }

    @Transactional
    fun updateRecord(recordId: Long, id: Long, title: String) {
        val record = recordRepository.findByIdOrElseThrow(recordId)
        val student = studentRepository.findByIdOrElseThrow(id)
        require(record.studentId == student.id) { "Record does not belong to student" }
        record.updateTitle(title)
    }

    fun findAllFeedbackDetails(size: Int = 20, lastId: Long = 0L): List<FeedbackInfo> {
        return recordReadRepository.findAllFeedbacksWithTeacher(lastId, size).map { it.toFeedbackInfo() }
    }

    fun findFeedbackTicket(ticketId: Long): FeedbackTicketInfo {
        val ticket = feedbackTicketRepository.findByIdOrElseThrow(ticketId)
        return FeedbackTicketInfo(
            id = requireNotNull(ticket.id),
            feedbackItemId = requireNotNull(ticket.feedbackItem.id),
            studentId = ticket.student?.id,
            amount = ticket.amount
        )
    }

    private fun Record.toInfo(): RecordInfo = RecordInfo(
        id = requireNotNull(id),
        url = url,
        createdAt = createdDate
    )

    private fun FeedbackInfoProjection.toFeedbackInfo(): FeedbackInfo = FeedbackInfo(
        id = getId(),
        recordId = getRecordId(),
        teacherId = getTeacherId(),
        teacherName = getTeacherNickname(),
        teacherProfileImage = getTeacherProfileImage(),
        detail = getDetail(),
        createdAt = getUpdatedDate()
    )
}
