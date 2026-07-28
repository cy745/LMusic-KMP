package com.lalilu.lmedia

import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.MediaLibrarySource
import com.lalilu.lmedia.source.MusicKitSource
import com.lalilu.lmedia.source.mediaLibrarySourceContent
import com.lalilu.lmedia.source.musicKitSourceContent
import com.lalilu.lmedia.source.sandBoxFileSystemSourceContent
import com.lalilu.lmedia.source.sandbox.SandboxFileSystemSource

actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): LazyStaggeredGridContent? {
    if (this is SandboxFileSystemSource) return sandBoxFileSystemSourceContent(modifier)
    if (this is MediaLibrarySource) return mediaLibrarySourceContent(modifier)
    if (this is MusicKitSource) return musicKitSourceContent(modifier)
    return null
}
