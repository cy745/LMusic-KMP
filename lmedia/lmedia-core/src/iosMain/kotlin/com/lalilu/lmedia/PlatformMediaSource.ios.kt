package com.lalilu.lmedia

import com.lalilu.lmedia.source.sandbox.SandboxFileSystemSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
//    MediaLibrarySource,
//    MusicKitSource,
    SandboxFileSystemSource,
)