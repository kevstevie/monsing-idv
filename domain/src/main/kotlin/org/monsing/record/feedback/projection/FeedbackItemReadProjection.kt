package org.monsing.record.feedback.projection

@Suppress("TooManyFunctions")
interface FeedbackItemReadProjection {
    fun getId(): Long
    fun getDescription(): String
    fun getPrice(): Int
    fun getAmount(): Int
    fun getTeacherId(): Long
    fun getTeacherNickname(): String
    fun getTeacherProfileImage(): String?
    fun getTeacherVerified(): Boolean
    fun getTeacherDescription(): String?
    fun getTeacherGenderType(): String
    fun getTeacherExpertiseType(): String
}
