package com.lalilu.lmedia

import com.lalilu.lmedia.source.AndroidFileSystemSource
import com.lalilu.lmedia.source.mediastore.MediaStoreSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    MediaStoreSource(get()),
    AndroidFileSystemSource(get())
)