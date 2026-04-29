package org.monsing.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableAsync
@EnableScheduling
class AsyncConfig {

    @Bean
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = SCHEDULER_POOL_SIZE
        setThreadNamePrefix("scheduler-")
        initialize()
    }

    companion object {
        private const val SCHEDULER_POOL_SIZE = 4
    }
}
