package org.monsing.record

import org.monsing.record.projection.FeedbackInfoProjection
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface RecordReadRepository : Repository<Record, Long> {

    @Query(
        value = """
            SELECT f.id, f.record_id AS recordId, f._detail AS detail,
                   f.updated_date AS updatedDate, t.id AS teacherId,
                   t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage
            FROM feedback f
            JOIN teacher t ON f.teacher_id = t.id
            WHERE f.record_id = :recordId
        """,
        nativeQuery = true
    )
    fun findFeedbacksByRecordId(@Param("recordId") recordId: Long): List<FeedbackInfoProjection>

    @Query(
        value = """
            SELECT f.id, f.record_id AS recordId, f._detail AS detail,
                   f.updated_date AS updatedDate, t.id AS teacherId,
                   t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage
            FROM feedback f
            JOIN teacher t ON f.teacher_id = t.id
            WHERE f.id > :lastId
            ORDER BY f.id ASC
            LIMIT :size
        """,
        nativeQuery = true
    )
    fun findAllFeedbacksWithTeacher(
        @Param("lastId") lastId: Long,
        @Param("size") size: Int
    ): List<FeedbackInfoProjection>
}
