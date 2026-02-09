package org.monsing.wordfilter

import org.jj.ahocorasick.AhoCorasick
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.PageRequest

@Configuration
class WordFilterConfig {

    @Bean
    fun ahoCorasick(bannedWordRepository: BannedWordRepository): AhoCorasick {
        val builder = AhoCorasick.builder()
        val pageSize = 1000
        var page = 0

        do {
            val pageResult = bannedWordRepository.findAllWords(PageRequest.of(page, pageSize))
            pageResult.content.forEach { builder.addKeyword(it) }
            page++
        } while (pageResult.hasNext())

        return builder.build()
    }
}
