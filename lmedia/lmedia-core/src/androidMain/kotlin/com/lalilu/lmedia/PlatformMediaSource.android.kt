package com.lalilu.lmedia

import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import com.lalilu.lmedia.source.mediastore.MediaStoreSource
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    ::MediaStoreSource.reverseInject(),
    ::AndroidFileSystemSource.reverseInject(),
//    ::RemoteSource.reverseInject(),
    ::SubsonicSource.reverseInject(),
)