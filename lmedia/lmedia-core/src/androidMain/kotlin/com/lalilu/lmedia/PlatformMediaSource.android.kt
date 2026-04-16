package com.lalilu.lmedia

import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import com.lalilu.lmedia.source.mediastore.MediaStoreSource
import org.koin.core.scope.Scope
import com.lalilu.common.ext.reverseInject

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    ::MediaStoreSource.reverseInject(),
    ::AndroidFileSystemSource.reverseInject(),
)