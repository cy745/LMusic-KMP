package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaSource
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

data class PlatformMediaSource(
    val source: List<MediaSource>
) {
    companion object {
        internal fun provide(vararg source: MediaSource): PlatformMediaSource {
            return PlatformMediaSource(source.toList())
        }
    }
}

@Single(binds = [PlatformMediaSource::class])
fun provideMediaSource(scope: Scope): PlatformMediaSource {
    return scope.provideMediaSources()
}

expect fun Scope.provideMediaSources(): PlatformMediaSource