package com.lalilu.lmedia.domain.model

/**
 * [LAudio.extra] 中由所有数据源共享的标准字段。
 *
 * 数据源自己的定位信息（例如 uri、path、storeID）仍可直接放在 extra 中；下游业务只通过
 * 本文件提供的扩展属性读取公共字段，避免在各模块散落字符串 Key 和数值解析逻辑。
 */
object LAudioExtraKeys {
    const val ArtistId = "artistId"
    const val ArtistName = "artistName"
    const val AlbumId = "albumId"
    const val AlbumName = "albumName"
    const val AlbumArtist = "albumArtist"
    const val Genre = "genre"
    const val Composer = "composer"
    const val Lyricist = "lyricist"
    const val Comment = "comment"
    const val Track = "track"
    const val Disc = "disc"
    const val Date = "date"
    const val Duration = "duration"
    const val DateAdded = "dateAdded"
    const val DateModified = "dateModified"
}

val LAudio.artistId: String?
    get() = extra.nonBlank(LAudioExtraKeys.ArtistId)

val LAudio.artistName: String
    get() = extra.nonBlank(LAudioExtraKeys.ArtistName)
        ?: subtitle.takeIf { it.isNotBlank() }
        ?: "Unknown"

val LAudio.albumId: String?
    get() = extra.nonBlank(LAudioExtraKeys.AlbumId)

val LAudio.albumName: String?
    get() = extra.nonBlank(LAudioExtraKeys.AlbumName)

val LAudio.albumArtist: String?
    get() = extra.nonBlank(LAudioExtraKeys.AlbumArtist)

val LAudio.genre: String?
    get() = extra.nonBlank(LAudioExtraKeys.Genre)

val LAudio.composer: String?
    get() = extra.nonBlank(LAudioExtraKeys.Composer)

val LAudio.lyricist: String?
    get() = extra.nonBlank(LAudioExtraKeys.Lyricist)

val LAudio.comment: String?
    get() = extra.nonBlank(LAudioExtraKeys.Comment)

val LAudio.track: String?
    get() = extra.nonBlank(LAudioExtraKeys.Track)

val LAudio.disc: String?
    get() = extra.nonBlank(LAudioExtraKeys.Disc)

val LAudio.date: String?
    get() = extra.nonBlank(LAudioExtraKeys.Date)

val LAudio.duration: Long
    get() = extra.longValue(LAudioExtraKeys.Duration) ?: 0L

val LAudio.dateAdded: Long
    get() = extra.longValue(LAudioExtraKeys.DateAdded) ?: 0L

val LAudio.dateModified: Long
    get() = extra.longValue(LAudioExtraKeys.DateModified) ?: 0L

/** 按当前项目约定拆分组合歌手名称，并在拆分后去除空白与重复项。 */
fun LAudio.artistNames(): List<String> = artistName
    .split('/', ';', '、', ',', '，')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .ifEmpty { listOf("Unknown") }

/** 下游统一重组媒体库时使用的歌手标识，不依赖某个数据源的内部 ID。 */
fun LAudio.libraryArtistIds(): List<String> = artistNames()
    .map(::normalizeLibraryName)
    .map { "${LArtist.ID_PREFIX}$it" }

/** 专辑同名时由 albumArtist 区分；空 albumArtist 保持旧的 album_<name> 形式。 */
fun LAudio.libraryAlbumId(): String {
    val name = normalizeLibraryName(albumName ?: "Unknown")
    val artist = albumArtist?.let(::normalizeLibraryName).orEmpty()
    val identity = if (artist.isBlank()) name else "$name|$artist"
    return "${LAlbum.ID_PREFIX}$identity"
}

fun LAudio.libraryGenreId(): String? = genre
    ?.let(::normalizeLibraryName)
    ?.takeIf(String::isNotBlank)
    ?.let { "${LGenre.ID_PREFIX}$it" }

fun normalizeLibraryName(value: String): String = value
    .trim()
    .replace(Regex("\\s+"), " ")
    .ifBlank { "Unknown" }

private fun Map<String, String>?.nonBlank(key: String): String? =
    this?.get(key)?.takeIf { it.isNotBlank() }

private fun Map<String, String>?.longValue(key: String): Long? =
    this?.get(key)?.toLongOrNull()
