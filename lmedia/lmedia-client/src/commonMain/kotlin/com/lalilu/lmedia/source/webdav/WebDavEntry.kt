package com.lalilu.lmedia.source.webdav

/**
 * PROPFIND 返回的单个资源。[path] 是解码后的服务器绝对路径（不含服务器地址），
 * 例如 `/music/HoneyComeBear/02 Friend.flac`；它同时是歌曲身份与缓存键的输入。
 */
data class WebDavEntry(
    val path: String,
    val isDirectory: Boolean,
    val contentLength: Long = 0L,
    val etag: String? = null,
    val lastModified: String? = null,
    val contentType: String? = null,
) {
    val fileName: String get() = path.substringAfterLast('/')

    /** 不含扩展名的文件名，用于匹配同名歌词。 */
    val baseName: String get() = fileName.substringBeforeLast('.', fileName)

    /**
     * 内容指纹：服务器给出 etag 时用它，否则退到修改时间。
     *
     * 用于边车文件（封面/歌词）的本地缓存失效——远端换了图就得重新拉取。
     */
    val fingerprint: String get() = etag ?: lastModified ?: contentLength.toString()
}

/** 从路径派生的可读元数据；播放顺路提取到真 tag 后会被覆盖。 */
data class WebDavNaming(
    val title: String,
    val artist: String?,
    val album: String?,
    val track: String?,
)

/** WebDAV 上没有元数据 API，首扫只能从文件名与目录结构派生，命名规范决定首批结果质量。 */
internal object WebDavNamingRules {
    /** 支持扫描的音频扩展名（小写比较）。 */
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus", "wav",
        "wma", "ape", "mpc", "aif", "aiff", "alac", "dsf", "dff", "mka", "webm", "amr",
    )

    /** 形如 `01 `、`1.`、`003-` 的曲目号前缀。 */
    private val TRACK_PREFIX = Regex("^(\\d{1,4})[\\s._-]+")

    /** 可以直接当封面用的图片扩展名（小写比较）。 */
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /**
     * 约定俗成的封面文件名（不含扩展名，小写比较）。
     *
     * 只有这些名字才允许"目录里有多张图"时挑一张；否则目录里唯一的一张图才会被当成封面，
     * 避免把歌手照/内页图误当专辑封面。
     */
    private val COVER_BASE_NAMES = setOf(
        "cover", "folder", "albumart", "album", "front", "artwork", "thumb", "thumbnail",
    )

    private const val LYRIC_EXTENSION = "lrc"

    fun isAudioFile(path: String): Boolean =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

    fun isImageFile(path: String): Boolean =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    fun isLyricFile(path: String): Boolean =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase() == LYRIC_EXTENSION

    /**
     * 从同一目录的条目里挑专辑封面：优先约定名（cover/folder/albumart…），
     * 其次目录里唯一的那张图。**不会**在多张无名图里随便挑。
     */
    fun pickCover(entries: List<WebDavEntry>): WebDavEntry? {
        val images = entries.filterNot { it.isDirectory }.filter { isImageFile(it.path) }
        if (images.isEmpty()) return null
        return images.firstOrNull { it.baseName.lowercase() in COVER_BASE_NAMES }
            ?: images.singleOrNull()
    }

    /** 与音频同名的 `.lrc`（大小写不敏感）。 */
    fun pickLyric(entries: List<WebDavEntry>, audio: WebDavEntry): WebDavEntry? {
        val base = audio.baseName.lowercase()
        return entries.firstOrNull {
            !it.isDirectory && isLyricFile(it.path) && it.baseName.lowercase() == base
        }
    }

    /**
     * 从绝对路径派生歌名/歌手/专辑/曲目号。
     *
     * [rootPath] 是媒体库根目录，**必须传入**：只有相对库根计算层级，才能区分
     * `/Music/艺人/曲目.flac`（专辑与歌手都是艺人目录）和 `/Music/艺人/专辑/曲目.flac`
     * 这两种常见布局。
     *
     * 规则（按优先级）：
     * - 曲目号：文件名开头的数字前缀，派生后从歌名中移除。
     * - 歌手：文件名中的 `Artist - Title` 段优先；否则取相对库根的倒数第二个目录；
     *   只剩一级目录时退回该目录本身。
     * - 专辑：相对库根的最后一个目录。
     * - 歌名的兜底顺序：`Artist - Title` 的 Title → 去掉曲目号的文件名 → 原始文件名。
     */
    fun derive(path: String, rootPath: String = "/"): WebDavNaming {
        val fileName = path.substringAfterLast('/').takeIf(String::isNotBlank).orEmpty()
        val base = fileName.substringBeforeLast('.', fileName).trim()

        val rootSegments = rootPath.trim('/').split('/').filter(String::isNotBlank)
        val above = path.trim('/').split('/').dropLast(1).filter(String::isNotBlank)
        val relativeDirectories = if (
            above.size >= rootSegments.size && above.take(rootSegments.size) == rootSegments
        ) {
            above.drop(rootSegments.size)
        } else {
            above
        }

        val album = relativeDirectories.lastOrNull()
        val artistFromDirectories = if (relativeDirectories.size >= 2) {
            relativeDirectories[relativeDirectories.size - 2]
        } else {
            relativeDirectories.lastOrNull()
        }

        val trackMatch = TRACK_PREFIX.find(base)
        val track = trackMatch?.groupValues?.get(1)?.trimStart('0')?.ifBlank { "0" }
        val withoutTrack = trackMatch?.let { base.removePrefix(it.value).trim() } ?: base

        val split = withoutTrack.split(" - ", limit = 2)
        val fileNameArtist = split.getOrNull(0)?.trim()?.takeIf { split.size == 2 && it.isNotBlank() }
        val fileNameTitle = if (split.size == 2) split[1].trim() else withoutTrack

        return WebDavNaming(
            title = fileNameTitle.ifBlank { base }.ifBlank { fileName },
            artist = fileNameArtist ?: artistFromDirectories,
            album = album,
            track = track,
        )
    }
}
