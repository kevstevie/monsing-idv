package org.monsing.member.teacher.projection

interface TeacherListProjection {
    fun getId(): Long
    fun getNickname(): String
    fun getVerified(): Boolean
    fun getProfileImage(): String?
    fun getSummary(): String?
    fun getDescription(): String?
}

interface TeacherCareerProjection {
    fun getTeacherId(): Long
    fun getDetail(): String
    fun getPeriod(): String
}

interface TeacherPortfolioProjection {
    fun getTeacherId(): Long
    fun getUrl(): String
}
