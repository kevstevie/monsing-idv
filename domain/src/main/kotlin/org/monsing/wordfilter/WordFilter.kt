package org.monsing.wordfilter

import org.jj.ahocorasick.AhoCorasick
import org.springframework.stereotype.Component

@Component
class WordFilter(private val ahoCorasick: AhoCorasick) {

    fun filter(sentence: String): Boolean {
        return ahoCorasick.containsAny(sentence)
    }
}
