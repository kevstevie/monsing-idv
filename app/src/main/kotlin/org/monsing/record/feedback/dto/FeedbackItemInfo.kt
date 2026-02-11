package org.monsing.record.feedback.dto

import org.monsing.teacher.dto.TeacherBrief

data class FeedbackItemInfo(
    val id: Long,
    val teacher: TeacherBrief,
    val description: String,
    val price: Int,
    val amount: Int
)
