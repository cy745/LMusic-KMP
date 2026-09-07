package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

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
        ?: throw MediaContentUnavailableException("Media data unavailable for ${audio.id}")
}

/**
 * 解析封面时只允许访问仍启用的数据源。[timeoutMillis] 大于零时会先有限等待内容就绪；超时或
 * 来源不可用时仍允许数据源按自身能力尝试读取，以保留本地来源启动期间快速展示封面的行为。
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
    if (!isEnabled(source)) return null
    return source.dataSource.getPicture(audio, options)
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
