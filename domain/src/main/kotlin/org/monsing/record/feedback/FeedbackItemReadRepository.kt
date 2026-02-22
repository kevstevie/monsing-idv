package org.monsing.record.feedback

import org.monsing.record.feedback.projection.FeedbackItemReadProjection
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface FeedbackItemReadRepository : Repository<FeedbackItem, Long> {

    @Query(
        value = """
            SELECT fi.id, fi.description, fi.price, fi.amount,
                   t.id AS teacherId, t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage, t.verified AS teacherVerified,
                   t.description AS teacherDescription, t.gender_type AS teacherGenderType,
                   t.expertise_type AS teacherExpertiseType
            FROM feedback_item fi
            JOIN teacher t ON fi.teacher_id = t.id
            WHERE fi.id > :lastId
            ORDER BY fi.id ASC
            LIMIT :size
        """,
        nativeQuery = true
    )
    fun findAllWithTeacher(
        @Param("lastId") lastId: Long,
        @Param("size") size: Int
    ): List<FeedbackItemReadProjection>

    @Query(
        value = """
            SELECT fi.id, fi.description, fi.price, fi.amount,
                   t.id AS teacherId, t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage, t.verified AS teacherVerified,
                   t.description AS teacherDescription, t.gender_type AS teacherGenderType,
                   t.expertise_type AS teacherExpertiseType
            FROM feedback_item fi
            JOIN teacher t ON fi.teacher_id = t.id
            WHERE fi.teacher_id = :teacherId
            AND fi.id > :lastId
            ORDER BY fi.id ASC
            LIMIT :size
        """,
        nativeQuery = true
    )
    fun findAllByTeacherIdWithTeacher(
        @Param("teacherId") teacherId: Long,
        @Param("lastId") lastId: Long,
        @Param("size") size: Int
    ): List<FeedbackItemReadProjection>

    @Query(
        value = """
            SELECT fi.id, fi.description, fi.price, fi.amount,
                   t.id AS teacherId, t.nickname AS teacherNickname,
                   t.profile_image AS teacherProfileImage, t.verified AS teacherVerified,
                   t.description AS teacherDescription, t.gender_type AS teacherGenderType,
                   t.expertise_type AS teacherExpertiseType
            FROM feedback_item fi
            JOIN teacher t ON fi.teacher_id = t.id
            JOIN feedback_ticket ft ON ft.feedback_item_id = fi.id
            WHERE ft.student_id = :studentId
            AND fi.id > :lastId
            ORDER BY fi.id ASC
            LIMIT :size
        """,
        nativeQuery = true
    )
    fun findAllByStudentIdWithTeacher(
        @Param("studentId") studentId: Long,
        @Param("lastId") lastId: Long,
        @Param("size") size: Int
    ): List<FeedbackItemReadProjection>
}
