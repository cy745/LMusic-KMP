package com.lalilu.lmedia.domain.source

import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

/**
 * Aggregation of all registered [MediaSource] instances.
 * Platform-specific implementations provide the actual sources.
 */
data class PlatformMediaSource(
    val sources: List<MediaSource>,
    private val enablement: MediaSourceEnablement = MediaSourceEnablement.AlwaysEnabled,
) {
    init {
        val duplicatedNames = sources
            .groupingBy(MediaSource::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicatedNames.isEmpty()) {
            "MediaSource names must be unique: ${duplicatedNames.joinToString()}"
        }
    }

    companion object {
        fun provide(vararg source: MediaSource): PlatformMediaSource {
            return PlatformMediaSource(source.toList())
        }
    }

    val enabledSources: List<MediaSource>
        get() = sources.filter(::isEnabled)

    fun findSource(sourceName: String): MediaSource? =
        sources.firstOrNull { it.name == sourceName }

    /**
     * 返回仍处于启用状态的数据源。
     *
     * 内容消费者必须通过这个入口解析来源，避免数据库里保留的不可用歌曲在来源停用后继续触发
     * 网络连接、文件读取或无限等待内容就绪。
     */
    fun findEnabledSource(sourceName: String): MediaSource? =
        findSource(sourceName)?.takeIf(::isEnabled)

    fun isEnabled(source: MediaSource): Boolean = isEnabled(source.name)

    fun isEnabled(sourceName: String): Boolean = enablement.isEnabled(sourceName)

    fun setEnabled(sourceName: String, enabled: Boolean) {
        require(sources.any { it.name == sourceName }) {
            "MediaSource '$sourceName' is not registered"
        }
        enablement.setEnabled(sourceName, enabled)
    }
}

@Single
context(scope: Scope)
fun providePlatformMediaSource(): PlatformMediaSource {
    return PlatformMediaSource(
        sources = scope.getAll<MediaSource>(),
        enablement = scope.get<MediaSourceEnablement>(),
    )
}
