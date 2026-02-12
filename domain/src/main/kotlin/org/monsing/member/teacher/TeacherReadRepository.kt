package org.monsing.member.teacher

import org.monsing.member.teacher.projection.TeacherCareerProjection
import org.monsing.member.teacher.projection.TeacherListProjection
import org.monsing.member.teacher.projection.TeacherPortfolioProjection
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface TeacherReadRepository : Repository<Teacher, Long> {

    @Query(
        value = """
            SELECT t.id, t.nickname, t.verified,
                   t.profile_image AS profileImage,
                   t.summary, t.description
            FROM teacher t
            ORDER BY t.id ASC
        """,
        nativeQuery = true
    )
    fun findAllTeachersFlat(): List<TeacherListProjection>

    @Query(
        value = """
            SELECT tc.teacher_id AS teacherId, c.detail, c.period
            FROM teacher_careers tc
            JOIN career c ON tc.careers_id = c.id
            WHERE tc.teacher_id IN (:teacherIds)
        """,
        nativeQuery = true
    )
    fun findCareersByTeacherIds(@Param("teacherIds") teacherIds: List<Long>): List<TeacherCareerProjection>

    @Query(
        value = """
            SELECT tp.teacher_id AS teacherId, p.url
            FROM teacher_portfolios tp
            JOIN portfolio p ON tp.portfolios_id = p.id
            WHERE tp.teacher_id IN (:teacherIds)
        """,
        nativeQuery = true
    )
    fun findPortfoliosByTeacherIds(
        @Param("teacherIds") teacherIds: List<Long>
    ): List<TeacherPortfolioProjection>
}
