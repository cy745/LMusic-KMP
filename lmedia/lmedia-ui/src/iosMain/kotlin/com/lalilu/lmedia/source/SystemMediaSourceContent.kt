package com.lalilu.lmedia.source

import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.domain.source.Snapshot

fun MediaLibrarySource.mediaLibrarySourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val state by source().collectAsStateWithLifecycle(initialValue = Snapshot.Idle)

    return@LazyStaggeredGridContent {
        item(key = this@mediaLibrarySourceContent.name) {
            SourceCard(
                modifier = modifier,
                state = { state },
            )
        }
    }
}

fun MusicKitSource.musicKitSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val state by source().collectAsStateWithLifecycle(initialValue = Snapshot.Idle)

    return@LazyStaggeredGridContent {
        item(key = this@musicKitSourceContent.name) {
            SourceCard(
                modifier = modifier,
                state = { state },
            )
        }
    }
}
