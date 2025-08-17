package com.lalilu.lmusic.util

import co.touchlab.kermit.Logger


class KermitKoinLogger(
    private val logger: Logger
) : org.koin.core.logger.Logger() {
    override fun display(level: org.koin.core.logger.Level, msg: org.koin.core.logger.MESSAGE) {
        when (level) {
            org.koin.core.logger.Level.DEBUG -> logger.d(msg)
            org.koin.core.logger.Level.INFO -> logger.i(msg)
            org.koin.core.logger.Level.WARNING -> logger.w(msg)
            org.koin.core.logger.Level.ERROR -> logger.e(msg)
            org.koin.core.logger.Level.NONE -> {
                // do nothing
            }
        }
    }
}