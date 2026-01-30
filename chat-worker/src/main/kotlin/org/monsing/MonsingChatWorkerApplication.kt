package org.monsing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MonsingChatWorkerApplication

fun main(args: Array<String>) {
    runApplication<MonsingChatWorkerApplication>(args = args)
}

