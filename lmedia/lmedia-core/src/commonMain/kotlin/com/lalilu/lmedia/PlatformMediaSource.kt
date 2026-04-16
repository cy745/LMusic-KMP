package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaSource
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

data class PlatformMediaSource(
    val sources: List<MediaSource>
) {
    companion object {
        internal fun provide(vararg source: MediaSource): PlatformMediaSource {
            return PlatformMediaSource(source.toList())
        }
    }
}

@Single(createdAtStart = true)
fun provideMediaSource(scope: Scope): PlatformMediaSource {
    val platformMediaSource = scope.provideMediaSources().sources
    val source = scope.getKoin().getAll<MediaSource>()

    return PlatformMediaSource(platformMediaSource + source)
        .apply { sources.forEach { it.init() } }
}

expect fun Scope.provideMediaSources(): PlatformMediaSource