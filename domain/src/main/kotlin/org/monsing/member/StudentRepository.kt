package org.monsing.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<Student, Long> {

    @Query("SELECT s FROM Student s JOIN Record r ON s.id = r.studentId WHERE r.id = :recordId")
    fun findByRecordId(recordId: Long): Student
}
