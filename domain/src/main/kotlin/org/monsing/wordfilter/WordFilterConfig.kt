package org.monsing.wordfilter

import org.jj.ahocorasick.AhoCorasick
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WordFilterConfig {

    @Bean
    fun ahoCorasick(bannedWordRepository: BannedWordRepository): AhoCorasick {
        val words = bannedWordRepository.findAll().map { it.word }

        val ahoCorasick = AhoCorasick.builder().addKeywords(words).build()

        return ahoCorasick
    }
}
