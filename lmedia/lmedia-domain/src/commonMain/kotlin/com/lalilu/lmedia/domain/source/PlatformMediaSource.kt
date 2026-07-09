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
    companion object {
        fun provide(vararg source: MediaSource): PlatformMediaSource {
            return PlatformMediaSource(source.toList())
        }
    }
}

@Single
context(scope: Scope)
fun providePlatformMediaSource(): PlatformMediaSource {
    return PlatformMediaSource(scope.getAll())
        .apply { sources.forEach { it.init() } }
}