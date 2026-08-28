package com.lalilu.lmedia.domain.source

import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

/**
 * Aggregation of all registered [MediaSource] instances.
 * Platform-specific implementations provide the actual sources.
 */
data class PlatformMediaSource(
    val sources: List<MediaSource>
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
}

@Single
context(scope: Scope)
fun providePlatformMediaSource(): PlatformMediaSource {
    return PlatformMediaSource(
        scope.getAll<MediaSource>()
    )
        .apply { sources.forEach { it.init() } }
}
