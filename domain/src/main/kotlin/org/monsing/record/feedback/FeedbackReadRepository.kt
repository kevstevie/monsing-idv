package org.monsing.record.feedback

import org.monsing.record.feedback.projection.FeedbackDetailReadProjection
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface FeedbackReadRepository : Repository<Feedback, Long> {

    @Query(
        value = """
            SELECT f.id, f.record_id AS recordId, f._detail AS detail,
                   f.updated_date AS updatedDate,
                   t.id AS teacherId, t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage,
                   s.id AS studentId, s.nickname AS studentNickname,
                   s.profile_image AS studentProfileImage
            FROM feedback f
            JOIN teacher t ON f.teacher_id = t.id
            JOIN record r ON f.record_id = r.id
            JOIN student s ON r.student_id = s.id
            WHERE f.teacher_id = :teacherId
        """,
        nativeQuery = true
    )
    fun findDetailsByTeacherId(@Param("teacherId") teacherId: Long): List<FeedbackDetailReadProjection>

    @Query(
        value = """
            SELECT f.id, f.record_id AS recordId, f._detail AS detail,
                   f.updated_date AS updatedDate,
                   t.id AS teacherId, t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage,
                   s.id AS studentId, s.nickname AS studentNickname,
                   s.profile_image AS studentProfileImage
            FROM feedback f
            JOIN teacher t ON f.teacher_id = t.id
            JOIN record r ON f.record_id = r.id
            JOIN student s ON r.student_id = s.id
            WHERE r.student_id = :studentId
        """,
        nativeQuery = true
    )
    fun findDetailsByStudentId(@Param("studentId") studentId: Long): List<FeedbackDetailReadProjection>
}
