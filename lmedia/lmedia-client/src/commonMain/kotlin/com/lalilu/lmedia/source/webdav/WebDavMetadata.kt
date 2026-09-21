package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.model.toAudioExtra
import kotlinx.serialization.Serializable

/**
 * 从音频文件提取出来的元数据（本地缓存的持久化形态）。
 *
 * [fingerprint] 来自服务器给出的 etag / size / lastModified：文件在远端被替换后指纹改变，
 * 缓存记录随之失效并允许重新提取。
 *
 * [complete] 区分"只读到文件头"与"读到完整文件"：部分提取足以拿到标题与歌手，但时长与内嵌封面
 * 只有完整文件才可靠，因此完整文件到达时要再提取一次。
 */
@Serializable
internal data class WebDavMetadataRecord(
    val fingerprint: String,
    val complete: Boolean,
    val extractedAt: Long,
    /**
     * 上次尝试提取时缓存里已有多少字节。
     *
     * 用来判断"头部窗口试过但没读出东西"这件事是否已经发生过——没有它就会在每次缓存增长时
     * 反复重试同一个读不出结果的窗口。
     */
    val examinedBytes: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val track: String = "",
    val disc: String = "",
    val date: String = "",
    val genre: String = "",
    val duration: Long = 0L,
    val hasCover: Boolean = false,
) {
    /** 转回共享的稀疏 extra 字段映射，避免各数据源各自维护一套 key。 */
    fun toAudioExtra(
        sourceExtra: Map<String, String>,
        artistId: String? = null,
        albumId: String? = null,
    ): Map<String, String> = Metadata(
        title = title.takeIf(String::isNotBlank),
        album = album.takeIf(String::isNotBlank),
        artist = artist.takeIf(String::isNotBlank),
        albumArtist = albumArtist,
        genre = genre,
        track = track,
        disc = disc,
        date = date,
        duration = duration,
        // 完整文件才有可信时长，而 toAudioExtra 只在大于 0 时写入
    ).toAudioExtra(sourceExtra = sourceExtra, artistId = artistId, albumId = albumId)

    companion object {
        /** 指纹只由服务器可观测的属性构成，与提取方式无关。 */
        fun fingerprintOf(
            size: Long,
            etag: String?,
            lastModified: String?,
        ): String = "$size|${etag ?: lastModified ?: ""}"
    }
}

/** 提取结果：一路写进缓存，一路用于刷新媒体库里的那一首。 */
internal data class WebDavExtractedMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val track: String = "",
    val disc: String = "",
    val date: String = "",
    val genre: String = "",
    val duration: Long = 0L,
    val cover: ByteArray? = null,
) {
    /** 连标题都没读出来时视为"这次没提取到"，避免把文件名派生的信息覆盖成空。 */
    val isEmpty: Boolean
        get() = title.isBlank() && artist.isBlank() && album.isBlank()

    fun toRecord(
        fingerprint: String,
        complete: Boolean,
        extractedAt: Long,
        examinedBytes: Long = 0L,
    ): WebDavMetadataRecord = WebDavMetadataRecord(
        fingerprint = fingerprint,
        complete = complete,
        extractedAt = extractedAt,
        examinedBytes = examinedBytes,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        track = track,
        disc = disc,
        date = date,
        genre = genre,
        duration = duration,
        hasCover = cover != null && cover.isNotEmpty(),
    )
}
