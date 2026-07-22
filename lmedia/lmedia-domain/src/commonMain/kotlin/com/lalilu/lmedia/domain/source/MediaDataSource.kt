package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio

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
}
