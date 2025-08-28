package com.lalilu.lmedia

import com.lalilu.lmedia.source.JvmFileSystemSource
import com.lalilu.lmedia.source.RemoteSource
import com.lalilu.lmedia.source.WebDavSource
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.core.scope.Scope

actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    JvmFileSystemSource(get()),
    WebDavSource(),
    RemoteSource(get(), get()),
    SubsonicSource(get(), get())
)