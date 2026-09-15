package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException

/**
 * 沿当前方向尝试播放，单次导航内的失败沿方向跳过，且最多绕队列一周。
 *
 * - [skipPolicy] 在**尝试之前**求值：只有"用户确实请求了播放、且这次是真正的加载"才允许跳过；
 *   已经加载成功后的失败不跳，避免把一次播放错误升级成整队列连跳。
 * - 两次尝试之间队列发生变化即中止，不推测新的队列所有权。
 * - 预算用尽时调用 [stop] 并抛出首个失败，由调用方决定如何上报。
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
                try {
                    stop()
                } catch (stopFailure: Exception) {
                    if (stopFailure is CancellationException) throw stopFailure
                    // 停止失败不能顶替原始失败：保留为 suppressed，调用方仍看到首个加载失败。
                    originalFailure.addSuppressed(stopFailure)
                }
                throw originalFailure
            }
            target = next
        }
    }
}
