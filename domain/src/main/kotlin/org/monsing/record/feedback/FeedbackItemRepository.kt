package org.monsing.record.feedback

import org.monsing.member.teacher.Teacher
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FeedbackItemRepository : JpaRepository<FeedbackItem, Long> {

    fun findByTeacher(teacher: Teacher): MutableList<FeedbackItem>

    @EntityGraph(attributePaths = ["teacher"])
    override fun findAll(): List<FeedbackItem>

    @Query(
        """
        select f from FeedbackItem f
        join fetch f.teacher on f.id = f.teacher.id
        """
    )
    fun findByTeacherId(teacherId: Long): List<FeedbackItem>
}
