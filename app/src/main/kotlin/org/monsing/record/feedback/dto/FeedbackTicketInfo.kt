package org.monsing.record.feedback.dto

data class FeedbackTicketInfo(
    val id: Long,
    val feedbackItemId: Long,
    val studentId: Long?,
    val amount: Int
)
