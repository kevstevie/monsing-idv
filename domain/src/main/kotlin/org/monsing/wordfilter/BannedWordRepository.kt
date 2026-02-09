package org.monsing.wordfilter

import org.springframework.data.jpa.repository.JpaRepository

interface BannedWordRepository : JpaRepository<BannedWord, Long>
