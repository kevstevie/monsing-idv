package org.monsing.record.feedback

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import org.monsing.BaseEntity
import org.monsing.member.teacher.Teacher
import org.monsing.record.Record

private const val MAXIMUM_LENGTH = 3000

@Entity
class Feedback(
    @ManyToOne
    @JoinColumn(name = "record_id", nullable = false)
    val record: Record,

    @ManyToOne
    @JoinColumn(nullable = false)
    val teacher: Teacher,

    @Lob
    private var _detail: String? = null,

    @Enumerated(EnumType.STRING)
    var status: FeedbackStatus = FeedbackStatus.REQUESTED,
) : BaseEntity() {

    val detail: String
        get() = _detail ?: ""

    fun writeFeedback(detail: String) {
        require(detail.isNotBlank()) { "Detail must not be blank" }
        require(detail.length <= MAXIMUM_LENGTH) { "Detail must not exceed $MAXIMUM_LENGTH characters" }
        status = status.complete()
        this._detail = detail
    }
}
