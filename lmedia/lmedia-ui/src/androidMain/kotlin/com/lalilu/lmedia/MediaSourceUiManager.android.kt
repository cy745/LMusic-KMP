package com.lalilu.lmedia

import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.androidFileSystemSourceContent
import com.lalilu.lmedia.source.androidSandboxMediaSourceContent
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import com.lalilu.lmedia.source.mediaStoreSourceContent
import com.lalilu.lmedia.source.mediastore.MediaStoreSource
import com.lalilu.lmedia.source.sandbox.AndroidSandboxMediaSource

actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): LazyStaggeredGridContent? {
    if (this is AndroidSandboxMediaSource) return androidSandboxMediaSourceContent(modifier)
    if (this is AndroidFileSystemSource) return androidFileSystemSourceContent(modifier)
    if (this is MediaStoreSource) return mediaStoreSourceContent(modifier)
    return null
}
