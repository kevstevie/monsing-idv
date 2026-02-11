package org.monsing.record.dto

import java.time.LocalDateTime

data class RecordInfo(
    val id: Long,
    val url: String,
    val createdAt: LocalDateTime,
    val feedbacks: List<FeedbackInfo> = emptyList()
)
