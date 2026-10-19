package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

/**
 * Data source for media content (lyrics, pictures, playback data).
 */
interface MediaDataSource {
    companion object {
        val Empty = object : MediaDataSource {}
    }

    suspend fun getLyric(song: LAudio): String? = null

    // ── getPicture ──

    /**
     * 获取封面图片数据。
     * 新代码请使用 [getPicture] 带 [MediaFetchOptions] 的重载。
     */
    @Deprecated(
        message = "Use getPicture(song, options) instead",
        replaceWith = ReplaceWith("getPicture(song, MediaFetchOptions.EMPTY)")
    )
    suspend fun getPicture(song: LAudio): MediaData? = null

    /**
     * 获取封面图片数据，支持通过 [options] 传递尺寸等额外参数。
     * 默认实现调用无参版本，平台可按需 override。
     */
    suspend fun getPicture(song: LAudio, options: MediaFetchOptions): MediaData? =
        getPicture(song)

    // ── getMedia ──

    suspend fun getMedia(song: LAudio): MediaData? = null

    // ── bufferedProgress ──

    /**
     * 数据源自己维护的缓冲比例（0..1）。
     *
     * 只有代理 / 读穿缓存型数据源才需要实现：对它们来说"缓冲到哪里"不等于播放引擎
     * 本次拉了多远，而是"这首歌已经有多少内容是本地就绪的"。返回非 null 时，播放页的
     * 缓冲进度会改用它换算，而不是引擎上报的缓冲位置。
     *
     * 默认返回 null，表示沿用引擎上报。
     */
    fun bufferedProgress(song: LAudio): Flow<Float>? = null
}
