package com.lalilu.lmedia

import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.RemoteSource
import com.lalilu.lmedia.source.remoteSourceContent
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import com.lalilu.lmedia.source.subsonicSourceContent

expect fun MediaSource.platformMediaSourceContent(modifier: Modifier): LazyStaggeredGridContent?

fun MediaSource.content(modifier: Modifier = Modifier): LazyStaggeredGridContent? {
    return platformMediaSourceContent(modifier) ?: when (this) {
        is SubsonicSource -> subsonicSourceContent(modifier)
        is RemoteSource -> remoteSourceContent(modifier)
        else -> null
    }
}