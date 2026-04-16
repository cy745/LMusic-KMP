package com.lalilu.lmedia

import com.lalilu.lmedia.source.JvmFileSystemSource
import org.koin.core.scope.Scope
import com.lalilu.common.ext.reverseInject

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    ::JvmFileSystemSource.reverseInject(),
)