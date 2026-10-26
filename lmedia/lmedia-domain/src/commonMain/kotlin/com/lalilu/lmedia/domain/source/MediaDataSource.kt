package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow

/**
 * Data source for media content (lyrics, pictures, playback data).
 */
interface MediaDataSource {
    /** Optional Plan B content contract. Scope must change when account/server/root changes. */
    fun contentCacheScope(): String? = null

    /** Return null unless size and a reliable content revision are known; never use a signed URL as identity. */
    fun contentCacheIdentity(song: LAudio): MediaCacheIdentity? = null

    /** Inspect already cached bytes without fetching missing ranges or owning the playback cache.
     * The temporary local file is valid only inside [withLocalFile]. */
    suspend fun inspectCachedMedia(
        song: LAudio,
        prefixBytes: Long,
        totalBytes: Long,
        withLocalFile: suspend (suspend (String) -> Unit) -> Unit,
    ) = Unit

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

    // ── 通用缓存 ──

    /**
     * 访问原始媒体地址所需的请求头（认证等）。
     *
     * 只有会被通用缓存代理转发的源才需要实现：代理用它访问远端，播放器拿到的地址里不含凭据。
     * 默认无请求头 —— 公开音源什么都不用写就能被缓存。
     */
    fun originHeaders(song: LAudio): Map<String, String> = emptyMap()

    /**
     * 稳定的缓存键；返回 null 表示直接用原始地址当键。
     *
     * 带签名、每次访问都变化的地址必须覆盖它，否则缓存永远不命中。
     */
    fun cacheKey(song: LAudio): String? = null

    /**
     * 远端文件总长度；返回 null 表示未知。
     *
     * 知道了才能按 `Range` 分段取：seek 只需要补中间那段。不知道也不影响播 —— 代理退化成
     * 整文件透传，边播边缓存，只是没法从缓存里直接跳到还没听过的位置。
     */
    fun cacheTotalSize(song: LAudio): Long? = null

    // ── bufferedProgress ──

    /**
     * 该数据源的内容是否需要经由网络读取。
     *
     * 只用来回答一个问题：**「缓冲进度」对这份内容有没有意义**。
     *
     * 本地文件同样会被播放引擎上报 `bufferedPosition`——那是解码前的加载缓冲
     * （Media3 默认预读约 50s），不是网络进度；画成缓冲条会被误读成"仍在下载"。
     * 因此本地内容恒为 false，播放页不会为它渲染缓冲进度。
     *
     * 默认 false：本地文件系统 / 沙盒 / 系统媒体库这类数据源无需任何改动；在线数据源
     * （Remote / Subsonic / WebDAV 等）必须显式覆盖为 true。新源漏写只会少显示一条
     * 缓冲条，不会把本地内容的噪声带回来。
     */
    val isRemoteContent: Boolean get() = false

    /**
     * 数据源自己维护的缓冲比例（0..1）。
     *
     * 只有代理 / 读穿缓存型数据源才需要实现：对它们来说"缓冲到哪里"不等于播放引擎
     * 本次拉了多远，而是"这首歌已经有多少内容是本地就绪的"。返回非 null 时，播放页的
     * 缓冲进度会改用它换算，而不是引擎上报的缓冲位置。
     *
     * 默认返回 null，表示沿用引擎上报（仅对 [isRemoteContent] 为 true 的源生效）。
     */
    fun bufferedProgress(song: LAudio): Flow<Float>? = null
}
