package com.lalilu.lmusic.util

import coil3.util.Logger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker

object CoilLogger : Logger {
    override var minLevel: Logger.Level = Logger.Level.Debug
    private val logger = KotlinLogging.logger { }

    override fun log(
        tag: String,
        level: Logger.Level,
        message: String?,
        throwable: Throwable?
    ) {
        logger.at(
            level = when (level) {
                Logger.Level.Verbose -> Level.TRACE
                Logger.Level.Debug -> Level.DEBUG
                Logger.Level.Info -> Level.INFO
                Logger.Level.Warn -> Level.WARN
                Logger.Level.Error -> Level.ERROR
            },
            marker = object : Marker {
                override fun getName(): String = tag
            },
        ) {
            this.cause = throwable
            this.message = message ?: "<empty>"
        }
    }
}
