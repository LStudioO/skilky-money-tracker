package com.vstorchevyi.skilky.ai

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.vstorchevyi.skilky.config.AppConfig
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import ch.qos.logback.classic.Logger as LogbackLogger

class MemoryGuardTest {
    private val log: LogbackLogger = LoggerFactory.getLogger("MemoryGuardTest") as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun setup() {
        appender.start()
        log.addAppender(appender)
    }

    @AfterTest
    fun teardown() {
        log.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `does not warn when ai config is null`() {
        warnIfLowMemory(ai = null, totalMemoryBytes = 4L * GIB, log = log)
        appender.list.shouldBeEmpty()
    }

    @Test
    fun `does not warn when total memory is unknown`() {
        warnIfLowMemory(ai = aiConfig, totalMemoryBytes = null, log = log)
        appender.list.shouldBeEmpty()
    }

    @Test
    fun `does not warn at or above threshold`() {
        warnIfLowMemory(ai = aiConfig, totalMemoryBytes = 8L * GIB, log = log)
        appender.list.shouldBeEmpty()
    }

    @Test
    fun `warns below threshold with model name and observed memory`() {
        warnIfLowMemory(ai = aiConfig, totalMemoryBytes = 4L * GIB, log = log)
        appender.list.shouldHaveSize(1)
        val message = appender.list[0].formattedMessage
        message.shouldContain("4.0 GiB")
        message.shouldContain("gemma4:e4b")
        message.shouldContain("AI_MODEL")
    }

    private val aiConfig =
        AppConfig.AiConfig(
            baseUrl = "http://localhost:11434",
            model = "gemma4:e4b",
            timeoutSeconds = 60,
            keepAlive = "30m",
        )

    private companion object {
        const val GIB: Long = 1024L * 1024L * 1024L
    }
}
