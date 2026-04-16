package com.lalilu.lmusic.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level

object KoinLogger : org.koin.core.logger.Logger() {
    private val logger = KotlinLogging.logger { }

    override fun display(level: org.koin.core.logger.Level, msg: org.koin.core.logger.MESSAGE) {
        logger.at(
            level = when (level) {
                org.koin.core.logger.Level.NONE -> Level.TRACE
                org.koin.core.logger.Level.DEBUG -> Level.DEBUG
                org.koin.core.logger.Level.INFO -> Level.INFO
                org.koin.core.logger.Level.WARNING -> Level.WARN
                org.koin.core.logger.Level.ERROR -> Level.ERROR
            },
        ) {
            message = msg
        }
    }
}