package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 沿当前方向尝试播放，单次导航内的失败沿方向跳过，且最多绕队列一周。
 *
 * - [skipPolicy] 在**尝试之前**求值：只有"用户确实请求了播放、且这次是真正的加载"才允许跳过；
 *   已经加载成功后的失败不跳，避免把一次播放错误升级成整队列连跳。
 * - 两次尝试之间队列发生变化即中止，不推测新的队列所有权。
 * - 预算用尽（绕队列一周，或 [outOfBudget] 判定的总时长上限）时调用 [stop] 并抛出首个失败，
 *   由调用方决定如何上报。
 * - 每个失败槽位都会回调 [recordFailure]，失败原因按歌曲分别保留。
 *
 * 返回真正开始播放的槽位下标。
 */
internal suspend fun navigateWithFailureFallback(
    traversal: PlaybackFailureTraversal,
    initialIndex: Int,
    direction: PlaybackDirection,
    playbackMode: () -> PlaybackMode,
    candidates: () -> List<LAudio>,
    playableSlots: suspend (List<LAudio>) -> Set<Int>,
    skipPolicy: (LAudio) -> Boolean,
    recordFailure: suspend (LAudio, Exception) -> Unit,
    stop: suspend () -> Unit,
    attempt: suspend (Int, LAudio) -> Unit,
    /** 每次尝试**之前**求值：整轮跳过已经超时就不再继续占着调用方的边界（例如队列编辑锁）。 */
    outOfBudget: () -> Boolean = { false },
): Int {
    traversal.begin(direction)
    var target = initialIndex
    var originalFailure: Exception? = null

    while (true) {
        val list = candidates()
        if (target !in list.indices) {
            throw originalFailure ?: IllegalArgumentException("Invalid index: $target")
        }
        traversal.observeQueue(list.map { it.playbackId })
        val item = list[target]
        val skipAllowed = skipPolicy(item)
        try {
            attempt(target, item)
            return target
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            recordFailure(item, failure)
            if (!skipAllowed) throw failure
            if (originalFailure == null) originalFailure = failure
            // 队列在两次尝试之间被替换：旧的失败不再代表新的导航目标。
            if (candidates().map { it.playbackId } != list.map { it.playbackId }) throw failure
            if (outOfBudget()) {
                originalFailure.addSuppressed(IllegalStateException("Failure fallback budget exhausted"))
                stopPreferring(originalFailure, stop)
                throw originalFailure
            }
            val playable = playableSlots(list)
            // playableSlots 可能挂起（平台侧可能查数据库）：期间队列被替换就不能把旧下标套到新列表上。
            if (candidates().map { it.playbackId } != list.map { it.playbackId }) throw failure
            val next = traversal.next(
                ticket = traversal.generation,
                size = list.size,
                failedIndex = target,
                mode = playbackMode(),
                playable = { slot -> slot in playable },
            )
            if (next == null) {
                stopPreferring(originalFailure, stop)
                throw originalFailure
            }
            target = next
        }
    }
}

/** 停止失败不能顶替原始失败：保留为 suppressed，调用方仍看到首个加载失败。 */
private suspend fun stopPreferring(
    originalFailure: Exception,
    stop: suspend () -> Unit,
): Unit = try {
    stop()
} catch (stopFailure: Exception) {
    if (stopFailure is CancellationException) throw stopFailure
    originalFailure.addSuppressed(stopFailure)
}

/**
 * 可以尝试的槽位：可用、所属来源已就绪、且没有已记录的失败。
 *
 * 抽成公共规则而不是留在平台实现里，是为了让"哪些槽位允许尝试"能被独立测试——
 * 平台侧的假实现只会替掉调用，测不到真实过滤条件。失败按来源限定的 playbackId 判定，
 * 因此一个来源的失败不会排除另一个来源的同 ID 歌曲。
 */
internal fun playablePlaybackSlots(
    list: List<LAudio>,
    recordedFailures: Set<String>,
    sourceReady: (LAudio) -> Boolean,
): Set<Int> = list.indices.filter { slot ->
    val candidate = list[slot]
    candidate.available && sourceReady(candidate) && candidate.playbackId !in recordedFailures
}.toSet()

/**
 * 单次失败导航的总时长上限。单次加载最坏 30s（例如不可达的远程地址），没有上限时
 * "绕队列一周"最坏会变成 N×30s，而这段逻辑位于队列编辑边界内。
 */
internal val DefaultSkipNavigationBudget = 60.seconds
