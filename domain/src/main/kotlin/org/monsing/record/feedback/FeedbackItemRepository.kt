package org.monsing.record.feedback

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedbackItemRepository : JpaRepository<FeedbackItem, Long> {

    @EntityGraph(attributePaths = ["teacher"])
    override fun findAll(): List<FeedbackItem>
}
