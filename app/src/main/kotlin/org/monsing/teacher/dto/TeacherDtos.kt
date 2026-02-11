package org.monsing.teacher.dto

data class TeacherSummary(
    val id: Long,
    val name: String,
    val verified: Boolean,
    val careers: List<CareerInfo>,
    val portfolioUrls: List<String>,
    val profileImage: String?,
    val summary: String?,
    val description: String?
)

data class CareerInfo(
    val detail: String,
    val period: String
)

data class TeacherBrief(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val verified: Boolean,
    val description: String?,
    val genderType: String,
    val expertiseType: String
)
