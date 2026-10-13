package com.lalilu.lmedia.source.webdav

import kotlinx.serialization.Serializable

/**
 * WebDAV 数据源的持久化配置。
 *
 * [password] 使用明文保存：Basic 认证每次请求都需要原始密码，无法像 Subsonic 那样只保存
 * `md5(password + salt)`。UI 会提示用户改用服务端签发的「应用专用密码」来限制泄露影响。
 *
 * @property url 服务器根地址，例如 `https://dav.example.com`
 * @property rootPath 媒体库根目录，例如 `/music`
 * @property metaRemotePath 元数据同步目录（仅在 [metaSyncEnabled] 为真时使用）
 * @property cacheQuotaBytes 音频缓存配额上限，超出后按 LRU 淘汰
 */
@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val rootPath: String = DEFAULT_ROOT_PATH,
    val backgroundFetchEnabled: Boolean = false,
    val metaSyncEnabled: Boolean = false,
    val metaRemotePath: String = DEFAULT_META_PATH,
    val cacheQuotaBytes: Long = DEFAULT_CACHE_QUOTA_BYTES,
) {
    val isConfigured: Boolean
        get() = url.isNotBlank()

    companion object {
        const val DEFAULT_ROOT_PATH = "/"
        const val DEFAULT_META_PATH = "/.lmusic/"
        const val DEFAULT_CACHE_QUOTA_BYTES = 2L * 1024 * 1024 * 1024

        val Empty = WebDavConfig()
    }
}

/**
 * 规范化服务器地址：缺协议补 `http://`，去掉尾部 `/`，保证它可以直接作为拼接前缀使用。
 */
internal fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim()
    require(trimmed.isNotBlank()) { "请填写服务器地址" }
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    return withScheme.trimEnd('/')
}

/** 规范化目录路径：确保以 `/` 开头与结尾，供 PROPFIND 与 href 前缀匹配共用。 */
internal fun normalizeDirectoryPath(raw: String): String {
    val trimmed = raw.trim().ifBlank { WebDavConfig.DEFAULT_ROOT_PATH }
    val withLeading = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
    return if (withLeading.endsWith('/')) withLeading else "$withLeading/"
}
