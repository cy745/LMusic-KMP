package com.lalilu.lmedia

import com.lalilu.lmedia.source.WebDavSource
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource {
    return PlatformMediaSource.provide(
        ::WebDavSource.reverseInject(),
//        ::RemoteSource.reverseInject(),
        ::SubsonicSource.reverseInject(),
    )
}