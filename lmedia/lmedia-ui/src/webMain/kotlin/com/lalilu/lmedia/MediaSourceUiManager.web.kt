package com.lalilu.lmedia

import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.domain.source.MediaSource

actual fun MediaSource.platformMediaSourceContent(modifier: Modifier): LazyStaggeredGridContent? {
    return null
}
