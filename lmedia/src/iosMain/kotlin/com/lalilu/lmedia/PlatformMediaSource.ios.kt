package com.lalilu.lmedia

import com.lalilu.lmedia.source.MediaLibrarySource
import com.lalilu.lmedia.source.MusicKitSource
import com.lalilu.lmedia.source.RemoteSource
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    MediaLibrarySource,
    MusicKitSource,
    RemoteSource(get()),
    SubsonicSource(get(), get())
)