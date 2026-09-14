package com.lalilu.lmedia.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses the same mutex as enable/disable, but never holds the snapshot committer's mutex. */
internal suspend fun <T> Mutex.runWhileSourceEnabled(
    isEnabled: () -> Boolean,
    block: suspend () -> T,
): T = withLock {
    check(isEnabled()) { "数据源已停用，请启用后再操作文件" }
    block()
}
