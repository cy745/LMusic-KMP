package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 缓冲位置解析。
 *
 * 引擎上报的缓冲位置只代表"本次播放拉到了哪里"。对代理 / 读穿缓存型数据源来说，用户真正
 * 关心的是"这首歌已经有多少内容是本地就绪的"——那才是可以不等待下载直接跳过去的范围，
 * 而且包含之前播放与预取沉淀下来的部分。
 *
 * 数据源给出 [MediaDataSource.bufferedProgress] 时按其比例换算成时间，否则沿用引擎上报值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BufferedPositionResolver(
    currentItem: Flow<LAudio?>,
    engineBufferedPosition: Flow<Long>,
    duration: Flow<Long>,
    dataSourceOf: (LAudio) -> MediaDataSource?,
    scope: CoroutineScope,
) {
    val bufferedPosition: StateFlow<Long> = combine(
        currentItem.flatMapLatest { item -> bufferedProgressFlow(item, dataSourceOf) },
        engineBufferedPosition,
        duration,
    ) { ratio, enginePositionMs, durationMs ->
        if (ratio != null && durationMs > 0L) {
            (durationMs * ratio).toLong().coerceIn(0L, durationMs)
        } else {
            enginePositionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        }
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    /** 曲目切换时切换到新数据源的进度流；没有该能力时给出 null，表示沿用引擎上报。 */
    private fun bufferedProgressFlow(
        item: LAudio?,
        dataSourceOf: (LAudio) -> MediaDataSource?,
    ): Flow<Float?> {
        if (item == null) return flowOf(null)

        // 缓冲进度只是增强信息：数据源解析不出来（未注册、来源已停用、容器未就绪等）
        // 就按"没有额外缓冲进度"处理，退回引擎上报，不能让播放链路因此失败。
        val dataSource = try {
            dataSourceOf(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return flowOf(null)

        return dataSource.bufferedProgress(item) ?: flowOf(null)
    }
}
