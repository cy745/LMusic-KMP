package com.lalilu.lmedia.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot

@Composable
fun MediaSource.MediaLibrarySourceContent(modifier: Modifier) {
    val state by source().collectAsStateWithLifecycle(initialValue = Snapshot.Idle)
    SourceCard(
        modifier = modifier,
        state = { state },
    )
}

@Composable
fun MediaSource.MusicKitSourceContent(modifier: Modifier) {
    val state by source().collectAsStateWithLifecycle(initialValue = Snapshot.Idle)
    SourceCard(
        modifier = modifier,
        state = { state },
    )
}
