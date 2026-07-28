package com.lalilu.lmedia.source

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.common.ext.io
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.Dispatchers

fun RemoteSource.remoteSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val source by remember { source() }.collectAsState(
        initial = Snapshot.Empty,
        context = Dispatchers.io
    )

    return@LazyStaggeredGridContent {
        item(key = this@remoteSourceContent.name) {
            SourceCard(
                modifier = modifier,
                state = { source }
            )
        }
    }
}

