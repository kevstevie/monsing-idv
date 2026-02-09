package org.monsing.wordfilter

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class BannedWord(
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Id
    val id: Long? = null,
    val word: String
)
