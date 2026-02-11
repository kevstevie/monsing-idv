package org.monsing.record.response

import java.time.LocalDateTime

data class FeedbackResponse(
    val id: Long,
    val recordId: Long,
    val teacherId: Long,
    val teacherName: String,
    val teacherProfileImage: String?,
    val student: StudentInfoResponse? = null,
    val detail: String?,
    val createdAt: LocalDateTime?
)

data class StudentInfoResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?
)
