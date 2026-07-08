package com.lalilu.lmedia

import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

@Single(createdAtStart = true, binds = [PlatformMediaSource::class])
fun provideDomainMediaSource(scope: Scope): PlatformMediaSource {
    val sources = scope.getKoin().getAll<MediaSource>()

    return PlatformMediaSource(sources)
        .apply { sources.forEach { it.init() } }
}
