package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

/** The source is ready, but this particular song cannot be resolved. */
class AudioMediaMissingException : IllegalStateException("Audio media is missing")

/**
 * 等一首歌的封面出现时的兜底轮询间隔：正常情况下靠来源快照的变更通知立刻醒来，
 * 这个间隔只是防止"提取结果按批合并、快照没跟着变"时一直等到预算耗尽。
 */
private const val PENDING_TICK_MILLIS = 700L

/**
 * 只等待目标歌曲所属数据源，并在其内容能力就绪后解析实际媒体。
 * 无关数据源的加载或失败不会参与本次判断。
 */
suspend fun PlatformMediaSource.resolveMediaData(
    audio: LAudio,
    timeoutMillis: Long = 15_000L,
): MediaData {
    val source = findSource(audio.mediaSourceName)
        ?: throw MediaContentUnavailableException(
            "Media source '${audio.mediaSourceName}' not found for ${audio.id}"
        )
    if (!isEnabled(source)) {
        throw MediaContentUnavailableException(
            "Media source '${audio.mediaSourceName}' is disabled for ${audio.id}"
        )
    }
    source.requireContentReady(timeoutMillis)
    if (!isEnabled(source)) {
        throw MediaContentUnavailableException(
            "Media source '${audio.mediaSourceName}' was disabled while resolving ${audio.id}"
        )
    }
    return source.dataSource.getMedia(audio)
        ?: throw AudioMediaMissingException()
}

/**
 * 解析封面时只允许访问仍启用的数据源。[timeoutMillis] 大于零时会先有限等待内容就绪；超时或
 * 来源不可用时仍允许数据源按自身能力尝试读取，以保留本地来源启动期间快速展示封面的行为。
 *
 * 这个预算同时也是"这首歌的封面要等它出现"的预算：数据源说"还在准备"（见
 * [MediaItemContentPending]）时会在这段时间里等它落盘，而不是把"还没到"当成"没有"。
 * 不这么做的话，网络歌曲的封面在到达之前界面就已经按失败渲染过了——那正是"切歌先闪一下
 * 底色、再跳成封面"的来源。预算为零表示调用方要的是立刻的答案，不做任何等待。
 */
suspend fun PlatformMediaSource.resolvePictureData(
    audio: LAudio,
    options: MediaFetchOptions = MediaFetchOptions.EMPTY,
    timeoutMillis: Long = 0L,
): MediaData? {
    val source = findEnabledSource(audio.mediaSourceName) ?: return null
    if (timeoutMillis > 0L) {
        try {
            source.requireContentReady(timeoutMillis)
        } catch (_: TimeoutCancellationException) {
            // 等待窗口结束后仍允许本地来源直接读取封面。
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // 部分本地数据源在扫描完成前已经可以读取封面；保留一次直接读取的机会。
        }
    }
    if (timeoutMillis <= 0L) {
        return if (isEnabled(source)) source.dataSource.getPicture(audio, options) else null
    }
    return withTimeoutOrNull(timeoutMillis) { awaitPicture(source, audio, options) }
}

/**
 * 反复读封面，直到读到、或者读到"确实没有"、或者预算耗尽。
 *
 * 用 `withTimeoutOrNull` 而不是自己算时间差：预算的流逝必须跟着调用方所在的调度器走
 * （测试用虚拟时间，真实运行用真实时间），自己读墙上时钟会把两者混在一起。
 */
private suspend fun PlatformMediaSource.awaitPicture(
    source: MediaSource,
    audio: LAudio,
    options: MediaFetchOptions,
): MediaData? {
    var revision = source.snapshot.value?.revision
    while (true) {
        if (!isEnabled(source)) return null
        source.dataSource.getPicture(audio, options)?.let { return it }

        // 来源说"就是没有"、或者根本没有这个能力时立刻给结论，别让界面一直显示上一张。
        val pending = source.dataSource as? MediaItemContentPending ?: return null
        if (!pending.isItemContentPending(audio)) return null

        // 等到来源发布新快照（提取写入记录/封面时必发）或兜底间隔到点：兜底是因为提取结果
        // 可能按批合并，不是每条记录都会让快照的 revision 变一次。
        withTimeoutOrNull(PENDING_TICK_MILLIS) {
            source.snapshot.first { it != null && it.revision != revision }
        }
        revision = source.snapshot.value?.revision
    }
}

/** 解析歌词时等待目标来源就绪，并在停用期间立即停止后续内容访问。 */
suspend fun PlatformMediaSource.resolveLyricData(
    audio: LAudio,
): String? {
    val source = findEnabledSource(audio.mediaSourceName) ?: return null
    source.awaitContentReadyOrThrow()
    if (!isEnabled(source)) return null
    return source.dataSource.getLyric(audio)
}

/**
 * 当前歌曲已缓冲的区间（见 [BufferedRange]）；源不支持上报或无法判断时发空列表。
 *
 * 只做分发，不做等待：进度条要的是"现在缓冲到哪了"，为此卡在数据源就绪判断上没有意义。
 */
fun PlatformMediaSource.observeBufferProgress(audio: LAudio): Flow<List<BufferedRange>> {
    val source = findSource(audio.mediaSourceName) ?: return flowOf(emptyList())
    val progressSource = source.dataSource as? MediaSourceBufferProgress ?: return flowOf(emptyList())
    return progressSource.bufferProgress(audio.id)
}
