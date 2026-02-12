package org.monsing.record.feedback.projection

import java.time.LocalDateTime

interface FeedbackDetailReadProjection {
    fun getId(): Long
    fun getRecordId(): Long
    fun getDetail(): String?
    fun getUpdatedDate(): LocalDateTime?
    fun getTeacherId(): Long
    fun getTeacherNickname(): String
    fun getTeacherProfileImage(): String?
    fun getStudentId(): Long
    fun getStudentNickname(): String
    fun getStudentProfileImage(): String?
}
