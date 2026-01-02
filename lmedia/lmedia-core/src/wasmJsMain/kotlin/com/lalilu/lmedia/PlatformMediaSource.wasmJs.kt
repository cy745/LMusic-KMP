package com.lalilu.lmedia

import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource {
    return PlatformMediaSource.provide(
    )
}