package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData

/**
 * Engine 链式匹配路由器。
 *
 * 根据 [canHandle] 按注册顺序匹配第一个能处理给定 [MediaData] 的 Engine。
 * 注册顺序即优先级——排在前面的 Engine 优先匹配。
 *
 * 使用示例：
 * ```
 * val router = PlaybackEngineRouter(listOf(
 *     AVAudioPlayerEngine(),   // Bytes → 优先检查
 *     AVPlayerEngine(),        // Url → 兜底
 * ))
 * val engine = router.selectEngine(mediaData, audio)
 *     ?: throw NoEngineFoundException(mediaData, audio)
 * ```
 */
class PlaybackEngineRouter(
    val allEngines: List<PlaybackEngine>
) {
    /**
     * 按注册顺序匹配第一个能处理给定媒体数据的 Engine。
     * @return 匹配的 Engine，无匹配时返回 null
     */
    fun selectEngine(mediaData: MediaData, audio: LAudio): PlaybackEngine? {
        return allEngines.firstOrNull { it.canHandle(mediaData, audio) }
    }
}

class NoEngineFoundException(
    mediaData: MediaData,
    audio: LAudio
) : Exception("No engine found for audio='${audio.id}' (source=${audio.mediaSourceName}) " +
    "mediaData=${mediaData::class.simpleName}")
