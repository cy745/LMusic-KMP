package com.lalilu.lmusic

import com.lalilu.lmusic.util.DebugRecomposeLogger
import com.lalilu.lmusic.util.MemoryLogWriter
import com.skydoves.compose.stability.runtime.ComposeStabilityAnalyzer
import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

object Log {

    fun setup() {
        ComposeStabilityAnalyzer.setEnabled(true)
        ComposeStabilityAnalyzer.setLogger(DebugRecomposeLogger) // TODO 需要判断debug模式才开启

        val originAppender = KotlinLoggingConfiguration.direct.appender
        KotlinLoggingConfiguration.direct.appender = object : Appender {
            override fun log(loggingEvent: KLoggingEvent) {
                originAppender.log(loggingEvent)
                MemoryLogWriter.log(loggingEvent)
            }
        }
    }
}