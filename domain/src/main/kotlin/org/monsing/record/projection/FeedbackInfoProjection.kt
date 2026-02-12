package org.monsing.record.projection

import java.time.LocalDateTime

interface FeedbackInfoProjection {
    fun getId(): Long
    fun getRecordId(): Long
    fun getDetail(): String?
    fun getUpdatedDate(): LocalDateTime?
    fun getTeacherId(): Long
    fun getTeacherNickname(): String
    fun getTeacherProfileImage(): String?
}
