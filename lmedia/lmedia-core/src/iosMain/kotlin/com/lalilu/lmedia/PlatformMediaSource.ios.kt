package com.lalilu.lmedia

import com.lalilu.lmedia.source.sandbox.SandboxFileSystemSource
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
//    MediaLibrarySource,
//    MusicKitSource,
    SandboxFileSystemSource,
//    ::RemoteSource.reverseInject(),
    ::SubsonicSource.reverseInject(),
)