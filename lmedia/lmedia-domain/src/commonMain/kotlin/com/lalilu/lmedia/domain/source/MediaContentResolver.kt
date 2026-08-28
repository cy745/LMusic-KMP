package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio

/**
 * 只等待目标歌曲所属数据源，并在其内容能力就绪后解析实际媒体。
 * 无关数据源的加载或失败不会参与本次判断。
 */
suspend fun PlatformMediaSource.resolveMediaData(
    audio: LAudio,
    timeoutMillis: Long = 15_000L,
): MediaData {
    val source = sources.firstOrNull { it.name == audio.mediaSourceName }
        ?: throw MediaContentUnavailableException(
            "Media source '${audio.mediaSourceName}' not found for ${audio.id}"
        )
    source.requireContentReady(timeoutMillis)
    return source.dataSource.getMedia(audio)
        ?: throw MediaContentUnavailableException("Media data unavailable for ${audio.id}")
}
