package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaSource
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

data class PlatformMediaSource(
    val sources: List<MediaSource>
)

@Single(createdAtStart = true)
fun provideMediaSource(scope: Scope): PlatformMediaSource {
    val sources = scope.getKoin().getAll<MediaSource>()

    return PlatformMediaSource(sources)
        .apply { sources.forEach { it.init() } }
}