package com.lalilu.lmedia.domain.source

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
