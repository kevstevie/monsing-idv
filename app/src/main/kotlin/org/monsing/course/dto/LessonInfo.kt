package org.monsing.course.dto

data class LessonInfo(
    val id: Long,
    val dayOfWeek: String,
    val startTime: String,
    val isAvailable: Boolean,
    val isOnAir: Boolean = false
)
