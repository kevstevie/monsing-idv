package org.monsing.record.feedback

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FeedbackItemRepository : JpaRepository<FeedbackItem, Long> {

    @EntityGraph(attributePaths = ["teacher"])
    override fun findAll(): List<FeedbackItem>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update FeedbackItem fi set fi.amount = fi.amount - :amount where fi.id = :id and fi.amount >= :amount
    """
    )
    fun decreaseAmountById(id: Long, amount: Int): Int
}
