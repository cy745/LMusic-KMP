package com.lalilu.lmusic.util

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.lalilu.common.ext.io
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


data class MemoryLogItem(
    val index: Long,
    val severity: Severity,
    val message: String,
    val tag: String,
    val throwable: Throwable?,
    val timestamp: Long
) {
    override fun toString(): String {
        return "[$timestamp][${severity.name}][$tag]: $message"
    }
}

/**
 * 内存中存储启动后的日志
 *
 */
object MemoryLogWriter : LogWriter(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io
    val logs = mutableStateListOf<MemoryLogItem>()
    val logCount = mutableStateOf(0L)
    val mutex = Mutex()

    @OptIn(ExperimentalTime::class, InternalAPI::class)
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        launch {
            mutex.withLock {
                logs.add(
                    MemoryLogItem(
                        index = logCount.value++,
                        severity = severity,
                        message = message,
                        tag = tag,
                        throwable = throwable,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }
    }
}