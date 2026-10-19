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
 * 核心约束是"时长必须属于当前曲目"：切换曲目时若新曲目的时长还未知（在线内容很常见，
 * 元数据与解析结果都可能迟到），宁可显示"未知"，也绝不能沿用上一首的时长。
 *
 * 因此引擎上报的时长只有在**相对切曲那一刻发生过变化**之后才会被采信——只有此时才能确认
 * 它是为新曲目报出来的值，而不是上一首的残留。由此得到几条规则：
 *
 * - 切曲：只用当前曲目的元数据建立时长域，缺失就是 0（未知）；
 * - 同一曲目内元数据被数据源补全时，直接采用；
 * - 引擎的瞬时 0 不会把已知时长降级；
 * - 引擎值与当前值差异足够大时才修正，避免抖动让进度条比例跳动；
 * - 首个曲目没有"上一首"可污染，直接采信当时的引擎值。
 *
 * 所有需要"这首歌唱多久"的地方都应该读 [Playback.stableDuration]，而不是
 * [Playback.currentDuration]：后者是引擎的原始上报，会经过空档，也不保证属于当前曲目。
 */
internal class PlaybackDurationResolver(
    currentItem: Flow<LAudio?>,
    engineDuration: Flow<Long>,
    scope: CoroutineScope,
) {
    private val _stableDuration = MutableStateFlow(0L)
    val stableDuration: StateFlow<Long> = _stableDuration.asStateFlow()

    private var itemKey: Any? = null
    private var metadataDurationMs = 0L

    /** 切曲那一刻的引擎时长；用来判断之后的上报是否属于新曲目。 */
    private var engineDurationAtSwitchMs = 0L

    /** 引擎时长是否在切曲之后发生过变化。 */
    private var engineReportedAfterSwitch = false
    private var engineDurationTrusted = false

    init {
        combine(currentItem, engineDuration) { item, engine -> item to engine }
            .onEach { (item, engineDurationMs) -> resolve(item, engineDurationMs) }
            .launchIn(scope)
    }

    private fun resolve(item: LAudio?, engineDurationMs: Long) {
        val key = item?.id
        val metadata = item?.duration?.takeIf { it > 0L } ?: 0L

        if (key != itemKey) {
            val hadPreviousItem = itemKey != null
            itemKey = key
            metadataDurationMs = metadata
            engineDurationTrusted = false
            engineDurationAtSwitchMs = engineDurationMs
            // 首个曲目没有被上一首污染的引擎值，可以直接采信
            engineReportedAfterSwitch = !hadPreviousItem

            // 切曲：先只用当前曲目的元数据，未知就是 0
            _stableDuration.value = metadata
            // 注意这里不 return：首个曲目可以紧接着采信引擎值（见下方判断）
        }

        // 同一曲目：数据源可能在之后补全元数据
        if (metadata != metadataDurationMs) {
            metadataDurationMs = metadata
            if (!engineDurationTrusted && metadata > 0L) {
                _stableDuration.value = metadata
            }
        }

        if (engineDurationMs != engineDurationAtSwitchMs) {
            engineReportedAfterSwitch = true
        }

        // 引擎在切曲/停止瞬间发出的 0 不代表时长真的变成了 0
        if (engineDurationMs <= 0L) return
        // 还没为当前曲目报出过新的值：这是上一首的残留，不能采信
        if (!engineReportedAfterSwitch) return

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
