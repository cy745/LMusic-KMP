package com.lalilu.lmusic.util

import coil3.util.Logger

class KermitCoilLogger(
    private val logger: co.touchlab.kermit.Logger
) : Logger {
    override var minLevel: Logger.Level = Logger.Level.Debug

    override fun log(
        tag: String,
        level: Logger.Level,
        message: String?,
        throwable: Throwable?
    ) {
        when (level) {
            Logger.Level.Verbose -> logger.v(
                tag = tag,
                messageString = message ?: "<EMPTY>",
                throwable = throwable
            )

            Logger.Level.Debug -> logger.d(
                tag = tag,
                messageString = message ?: "<EMPTY>",
                throwable = throwable
            )

            Logger.Level.Info -> logger.i(
                tag = tag,
                messageString = message ?: "<EMPTY>",
                throwable = throwable
            )

            Logger.Level.Warn -> logger.w(
                tag = tag,
                messageString = message ?: "<EMPTY>",
                throwable = throwable
            )

            Logger.Level.Error -> logger.e(
                tag = tag,
                messageString = message ?: "<EMPTY>",
                throwable = throwable
            )
        }
    }
}
