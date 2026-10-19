package com.lalilu.lmedia.coil

import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * 封面等待窗口。
 *
 * 封面不是"一次请求就能拿到"的东西：远端源要先扫到同目录边车文件、等缓存长到能提取内嵌图。
 * 这段时间里第一次请求注定落空，而 [coil3.fetch.Fetcher] 返回 null 的语义是"我不认识这份数据"——
 * Coil 会继续找下一个 fetcher，找不到就抛 "Unable to create a fetcher that supports: ..." 结束整次请求。
 * 用户看到的就是"封面对应的那一行一直是灰的，直到某个别的变化让请求重来一遍"。
 */
internal const val CoverAwaitTimeoutMillis = 8_000L

private const val CoverAwaitInitialDelayMillis = 400L
private const val CoverAwaitMaxDelayMillis = 2_000L

/**
 * 在窗口内反复尝试取封面，拿到就返回；窗口结束仍没有则返回 null（由调用方决定怎么表达）。
 *
 * 取消会从 [resolve] / [delay] 的挂起点自然传播：请求被丢弃时不会继续占着线程等下去。
 */
internal suspend fun <T : Any> awaitCover(
    deadlineMillis: Long = CoverAwaitTimeoutMillis,
    resolve: suspend () -> T?,
): T? {
    val startedAt = TimeSource.Monotonic.markNow()
    var delayMillis = CoverAwaitInitialDelayMillis
    while (true) {
        resolve()?.let { return it }

        val elapsed = startedAt.elapsedNow().inWholeMilliseconds
        // 下一次重试会越过窗口时就不再等，把"没有封面"这个结论交回调用方
        if (elapsed >= deadlineMillis || elapsed + delayMillis >= deadlineMillis) return null

        delay(delayMillis)
        delayMillis = (delayMillis * 2).coerceAtMost(CoverAwaitMaxDelayMillis)
    }
}
