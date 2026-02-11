package org.monsing.record.feedback

import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.monsing.BaseEntity
import org.monsing.member.teacher.Teacher

@Entity
class FeedbackItem(
    @ManyToOne
    @JoinColumn(nullable = false)
    val teacher: Teacher,
    val description: String,
    val price: Int,
    var amount: Int,
    @OneToMany(mappedBy = "feedbackItem")
    val feedbackTickets: MutableList<FeedbackTicket> = mutableListOf()
) : BaseEntity() {

    fun decreaseAmount(purchaseAmount: Int) {
        require(amount >= purchaseAmount) { "Amount must be greater than or equal to purchase amount" }
        require(amount > 0) { "Amount must be greater than 0" }
        amount -= purchaseAmount
    }
}
