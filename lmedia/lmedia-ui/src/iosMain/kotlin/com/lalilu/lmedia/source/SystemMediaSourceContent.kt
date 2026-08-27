package com.lalilu.lmedia.source

import androidx.compose.ui.Modifier
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourcePipelineCard

fun MediaLibrarySource.mediaLibrarySourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    return@LazyStaggeredGridContent {
        item(key = this@mediaLibrarySourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "系统媒体库",
                description = "读取系统媒体资料库中的音频",
            )
        }
    }
}

fun MusicKitSource.musicKitSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    return@LazyStaggeredGridContent {
        item(key = this@musicKitSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "MusicKit",
                description = "读取 Apple Music 媒体资料库",
            )
        }
    }
}
