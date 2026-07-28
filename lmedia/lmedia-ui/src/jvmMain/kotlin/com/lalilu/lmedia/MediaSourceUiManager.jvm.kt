package com.lalilu.lmedia


import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.JvmFileSystemSource
import com.lalilu.lmedia.source.jvmFileSystemSourceContent


actual fun MediaSource.platformMediaSourceContent(
    modifier: Modifier
): LazyStaggeredGridContent? {
    if (this is JvmFileSystemSource) return jvmFileSystemSourceContent(modifier)
    return null
}