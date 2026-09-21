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
)

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

    fun isAudioFile(path: String): Boolean =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

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
