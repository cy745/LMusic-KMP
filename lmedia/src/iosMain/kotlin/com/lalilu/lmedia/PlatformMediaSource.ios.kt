package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaLibrarySource
import com.lalilu.lmedia.source.MusicKitSource
import com.lalilu.lmedia.source.RemoteSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    MediaLibrarySource,
    MusicKitSource,
    RemoteSource(get())
)