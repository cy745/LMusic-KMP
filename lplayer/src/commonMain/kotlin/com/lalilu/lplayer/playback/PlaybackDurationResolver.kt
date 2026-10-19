package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.absoluteValue
import kotlin.math.max

/** 时长漂移的绝对下限：小于 1s 的差异不修正。 */
private const val DurationDriftFloorMs = 1_000L

/** 元数据时长与引擎时长的允许误差（1/50 = 2%），超出才认为元数据不准。 */
private const val MetadataDriftDivisor = 50L

/** 已采用引擎时长后，同曲目内的修正阈值（1/100 = 1%）。 */
private const val EngineDriftDivisor = 100L

/**
 * 按曲目维护的稳定时长。
 *
 * 引擎在切歌瞬间会先报 0、再报真实值；如果把这个序列直接交给 UI，进度条的比例会先归零
 * 再跳回来，时长文本也会闪一下 `00:00`。这里把"曲目元数据时长"和"引擎上报时长"合成一个值：
 *
 * - 切歌时立刻用元数据建立时长域，没有元数据才退回引擎值；
 * - 同一曲目内，引擎的瞬时 0 不会把已知时长降级；
 * - 只有与当前值差异足够大时才接受引擎修正，避免抖动让进度条跳动。
 *
 * 所有需要"这首歌唱多久"的地方都应该读 [Playback.stableDuration]，而不是
 * [Playback.currentDuration]：后者是引擎的原始上报，会经过空档。
 */
internal class PlaybackDurationResolver(
    currentItem: Flow<LAudio?>,
    engineDuration: Flow<Long>,
    scope: CoroutineScope,
) {
    private val _stableDuration = MutableStateFlow(0L)
    val stableDuration: StateFlow<Long> = _stableDuration.asStateFlow()

    private var itemKey: Any? = null
    private var engineDurationTrusted = false

    init {
        combine(currentItem, engineDuration) { item, engine -> item to engine }
            .onEach { (item, engineDurationMs) -> resolve(item, engineDurationMs) }
            .launchIn(scope)
    }

    private fun resolve(item: LAudio?, engineDurationMs: Long) {
        val key = item?.id
        if (key == null) {
            itemKey = null
            engineDurationTrusted = false
            _stableDuration.value = 0L
            return
        }

        if (key != itemKey) {
            itemKey = key
            engineDurationTrusted = false
            // 切歌：元数据通常已经就有，不必等引擎加载
            _stableDuration.value = item.duration.takeIf { it > 0L }
                ?: engineDurationMs.coerceAtLeast(0L)
            return
        }

        // 引擎在切歌/停止瞬间发出的 0 不代表时长真的变成了 0
        if (engineDurationMs <= 0L) return

        val current = _stableDuration.value
        if (!engineDurationTrusted) {
            val drift = (engineDurationMs - current).absoluteValue
            if (current <= 0L || drift > driftTolerance(current, MetadataDriftDivisor)) {
                _stableDuration.value = engineDurationMs
                engineDurationTrusted = true
            }
            return
        }

        val drift = (engineDurationMs - current).absoluteValue
        if (drift <= driftTolerance(current, EngineDriftDivisor)) return
        _stableDuration.value = engineDurationMs
    }

    private fun driftTolerance(currentMs: Long, divisor: Long): Long =
        max(DurationDriftFloorMs, currentMs / divisor)
}
