package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.source.RemoteSource
import com.lalilu.lmedia.source.RemoteSourceContent
import com.lalilu.lmedia.source.SubsonicSourceContent
import com.lalilu.lmedia.source.subsonic.SubsonicSource

@Composable
expect fun MediaSource.platformMediaSourceContent(modifier: Modifier): Boolean

@Composable
fun MediaSource.Content(modifier: Modifier = Modifier) {
    if (platformMediaSourceContent(modifier)) {
        return
    }

    when (this) {
        is SubsonicSource -> SubsonicSourceContent(modifier)
        is RemoteSource -> RemoteSourceContent(modifier)
    }
}