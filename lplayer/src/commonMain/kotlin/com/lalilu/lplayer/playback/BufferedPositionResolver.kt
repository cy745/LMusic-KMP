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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 缓冲位置的来源选择。
 *
 * 「缓冲进度」回答的是"这首歌已经有多少内容是网络就绪的"，因此它只对**在线内容**有意义。
 * 本地文件同样会被播放引擎上报 `bufferedPosition`（那是解码前的加载缓冲，Media3 默认
 * 预读约 50s），把它画成缓冲条会被误读成"仍在下载"，所以本地内容一律不提供缓冲位置。
 */
private sealed interface Buffering {
    /** 本地内容：没有"缓冲到哪里"这回事。 */
    data object Unavailable : Buffering

    /** 在线但没有自带缓存比例：沿用引擎上报的缓冲位置。 */
    data object FromEngine : Buffering

    /** 在线且数据源能给出缓存比例（读穿缓存型）：按比例换算。 */
    data class FromRatio(val ratio: Float) : Buffering
}

/**
 * 缓冲位置解析。
 *
 * 引擎上报的缓冲位置只代表"本次播放拉到了哪里"。对代理 / 读穿缓存型数据源来说，用户真正
 * 关心的是"这首歌已经有多少内容是本地就绪的"——那才是可以不等待下载直接跳过去的范围，
 * 而且包含之前播放与预取沉淀下来的部分。
 *
 * 三种情况：
 * - 本地内容（[MediaDataSource.isRemoteContent] 为 false）→ 0，播放页因此不渲染缓冲条；
 * - 在线且数据源给出 [MediaDataSource.bufferedProgress] → 按比例换算成时间；
 * - 在线但没有该能力 → 沿用引擎上报值。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BufferedPositionResolver(
    currentItem: Flow<LAudio?>,
    engineBufferedPosition: Flow<Long>,
    duration: Flow<Long>,
    dataSourceOf: (LAudio) -> MediaDataSource?,
    scope: CoroutineScope,
    private val preferEngine: Boolean = false,
) {
    val bufferedPosition: StateFlow<Long> = combine(
        currentItem.flatMapLatest { item -> bufferingOf(item, dataSourceOf) },
        engineBufferedPosition,
        duration,
    ) { buffering, enginePositionMs, durationMs ->
        val engineValue = enginePositionMs.coerceIn(
            0L,
            durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
        )
        when (buffering) {
            Buffering.Unavailable -> 0L
            Buffering.FromEngine -> engineValue
            is Buffering.FromRatio ->
                if (durationMs > 0L) {
                    (durationMs * buffering.ratio).toLong().coerceIn(0L, durationMs)
                } else {
                    engineValue
                }
        }
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    /** 当前曲目该用哪种缓冲来源。曲目切换时整体替换，不残留上一首的判断。 */
    private fun bufferingOf(
        item: LAudio?,
        dataSourceOf: (LAudio) -> MediaDataSource?,
    ): Flow<Buffering> {
        if (item == null) return flowOf(Buffering.Unavailable)

        // 缓冲进度只是增强信息：数据源解析不出来（未注册、来源已停用、容器未就绪等）
        // 就按"没有额外缓冲进度"处理，退回引擎上报，不能让播放链路因此失败。
        val dataSource = try {
            dataSourceOf(item)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return flowOf(Buffering.FromEngine)

        if (!dataSource.isRemoteContent) return flowOf(Buffering.Unavailable)
        if (preferEngine) return flowOf(Buffering.FromEngine)

        return dataSource.bufferedProgress(item)
            ?.map { Buffering.FromRatio(it) }
            ?: flowOf(Buffering.FromEngine)
    }
}
