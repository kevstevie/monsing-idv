package org.monsing.course.dto

data class CourseInfo(
    val id: Long,
    val teacherId: Long,
    val name: String,
    val description: String,
    val curriculum: String,
    val duration: Int,
    val price: Int,
    val minimumLessonCount: Int
)
