package org.monsing.record.feedback

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.monsing.BaseEntity
import org.monsing.member.Student

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["student_id", "feedback_item_id"])])
class FeedbackTicket(

    @ManyToOne
    @JoinColumn(nullable = false)
    val feedbackItem: FeedbackItem,

    @ManyToOne
    @JoinColumn(nullable = false)
    var student: Student?,

    private var _amount: Int
) : BaseEntity() {

    @Version
    var version: Int? = null

    val amount: Int
        get() = _amount

    fun decreaseAmount(purchaseAmount: Int) {
        require(_amount >= purchaseAmount) { "Amount must be greater than or equal to purchase amount" }
        require(_amount > 0) { "Amount must be greater than 0" }
        _amount -= purchaseAmount
    }

    fun increaseAmount() {
        _amount++
    }

    fun increaseAmount(additionalAmount: Int) {
        require(additionalAmount > 0) { "Additional amount must be greater than 0" }
        _amount += additionalAmount
    }
}
