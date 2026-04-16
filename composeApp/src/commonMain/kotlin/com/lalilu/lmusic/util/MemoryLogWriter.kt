package com.lalilu.lmusic.util

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.lalilu.common.ext.io
import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext


data class MemoryLogItem(
    val index: Long,
    val level: Level,
    val message: String,
    val tag: String,
    val throwable: Throwable?,
    val timestamp: Long
) {
    override fun toString(): String {
        return "[$timestamp][${level.name}][$tag]: $message"
    }
}

/**
 * 内存中存储启动后的日志
 *
 */
object MemoryLogWriter : Appender, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    val logs = mutableStateListOf<MemoryLogItem>()
    val logCount = mutableStateOf(0L)

    override fun log(loggingEvent: KLoggingEvent) {
        logs.add(
            MemoryLogItem(
                index = logCount.value++,
                level = loggingEvent.level,
                message = loggingEvent.message ?: "<empty>",
                tag = loggingEvent.loggerName,
                throwable = loggingEvent.cause,
                timestamp = loggingEvent.timestamp
            )
        )
    }
}