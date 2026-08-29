package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable

/**
 * 音轨元数据（Taglib 扫描阶段的中转 DTO）。
 *
 * 由平台 Taglib 实现（Android/JVM 的 JNI 绑定、iOS 的 C-Interop）在扫描时生成，
 * 扫描完成后应立即通过 [toAudioExtra] 写入 [LAudio.extra] 落地为稀疏字段，
 * 该类型本身不持久化、不进入数据库。
 *
 * ⚠️ 包名/构造器签名是 JNI 侧契约的一部分：移动或改签名必须同步
 * taglib fork 的 `bindings/jni/taglib_jni.h`（TAGLIB_JNI_METADATA_CLASS /
 * TAGLIB_JNI_METADATA_CTOR_SIG），否则 `System.loadLibrary` 会因签名不匹配
 * 在启动期直接失败。
 */
@Serializable
data class Metadata(
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumArtist: String = "",
    val composer: String = "",
    val lyricist: String = "",
    val comment: String = "",
    val genre: String = "",
    val track: String = "",
    val disc: String = "",
    val date: String = "",
    val duration: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L
) {
    companion object {
        val EMPTY = Metadata()
    }
}

/**
 * 将扫描阶段产生的临时 [Metadata] 转换为歌曲长期保存的稀疏 extra。
 * 空字符串和 0 不写入，数据源自己的 uri、path 等字段通过 [sourceExtra] 一并保留。
 */
fun Metadata.toAudioExtra(
    sourceExtra: Map<String, String> = emptyMap(),
    artistId: String? = null,
    albumId: String? = null,
): Map<String, String> = buildMap {
    putAll(sourceExtra)
    putIfNotBlank(LAudioExtraKeys.ArtistId, artistId)
    putIfNotBlank(LAudioExtraKeys.ArtistName, artist)
    putIfNotBlank(LAudioExtraKeys.AlbumId, albumId)
    putIfNotBlank(LAudioExtraKeys.AlbumName, album)
    putIfNotBlank(LAudioExtraKeys.AlbumArtist, albumArtist)
    putIfNotBlank(LAudioExtraKeys.Genre, genre)
    putIfNotBlank(LAudioExtraKeys.Composer, composer)
    putIfNotBlank(LAudioExtraKeys.Lyricist, lyricist)
    putIfNotBlank(LAudioExtraKeys.Comment, comment)
    putIfNotBlank(LAudioExtraKeys.Track, track)
    putIfNotBlank(LAudioExtraKeys.Disc, disc)
    putIfNotBlank(LAudioExtraKeys.Date, date)
    putIfPositive(LAudioExtraKeys.Duration, duration)
    putIfPositive(LAudioExtraKeys.DateAdded, dateAdded)
    putIfPositive(LAudioExtraKeys.DateModified, dateModified)
}

private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let { put(key, it) }
}

private fun MutableMap<String, String>.putIfPositive(key: String, value: Long) {
    value.takeIf { it > 0L }?.let { put(key, it.toString()) }
}
