package org.monsing.wordfilter

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BannedWordRepository : JpaRepository<BannedWord, Long> {
    @Query("SELECT b.word FROM BannedWord b")
    fun findAllWords(pageable: Pageable): Page<String>
}
