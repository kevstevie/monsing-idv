package org.monsing.course

import jakarta.persistence.LockModeType
import java.time.LocalTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface LessonRepository : JpaRepository<Lesson, Long> {

    @Query(
        """
        SELECT COUNT(l) > 0
        FROM Course c
        JOIN c.lessons l
        WHERE c.teacherId = :teacherId
        AND l.id = :id
        """
    )
    fun existsByTeacherIdAndLessonId(teacherId: Long, id: Long): Boolean
    fun findAllByStudentId(id: Long): List<Lesson>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
            select l from Course c
            join c.lessons l
            where c.teacherId = :teacherId
            and l.lessonSchedule.dayOfWeek = :dayOfWeek
            and l.lessonSchedule.startTime >= :startTime
            and l.lessonSchedule.startTime < :endTime
        """
    )
    fun findByTeacherAndSchedule(
        teacherId: Long,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ): List<Lesson>
}
