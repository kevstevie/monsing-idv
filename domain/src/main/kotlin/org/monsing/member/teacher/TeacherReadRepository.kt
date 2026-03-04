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
            WHERE t.id = :id
        """,
        nativeQuery = true
    )
    fun findTeacherFlatById(@Param("id") id: Long): TeacherListProjection?

    @Query(
        value = """
            SELECT t.id, t.nickname, t.verified,
                   t.profile_image AS profileImage,
                   t.summary, t.description
            FROM teacher t
            WHERE t.id > :lastId
            ORDER BY t.id ASC
            LIMIT :size
        """,
        nativeQuery = true
    )
    fun findAllTeachersFlat(
        @Param("lastId") lastId: Long,
        @Param("size") size: Int
    ): List<TeacherListProjection>

    @Query(
        value = """
            SELECT c.teacher_id AS teacherId, c.detail, c.period
            FROM career c
            WHERE c.teacher_id IN (:teacherIds)
        """,
        nativeQuery = true
    )
    fun findCareersByTeacherIds(@Param("teacherIds") teacherIds: List<Long>): List<TeacherCareerProjection>

    @Query(
        value = """
            SELECT p.teacher_id AS teacherId, p.url
            FROM portfolio p
            WHERE p.teacher_id IN (:teacherIds)
        """,
        nativeQuery = true
    )
    fun findPortfoliosByTeacherIds(
        @Param("teacherIds") teacherIds: List<Long>
    ): List<TeacherPortfolioProjection>
}
