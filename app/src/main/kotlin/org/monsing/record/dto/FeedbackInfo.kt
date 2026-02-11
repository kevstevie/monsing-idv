package org.monsing.record.dto

import java.time.LocalDateTime

data class FeedbackInfo(
    val id: Long,
    val recordId: Long,
    val teacherId: Long,
    val teacherName: String,
    val teacherProfileImage: String?,
    val detail: String?,
    val createdAt: LocalDateTime?
)

data class FeedbackDetailInfo(
    val id: Long,
    val recordId: Long,
    val teacherId: Long,
    val teacherName: String,
    val teacherProfileImage: String?,
    val studentId: Long,
    val studentName: String,
    val studentProfileImage: String?,
    val detail: String?,
    val createdAt: LocalDateTime?
)
