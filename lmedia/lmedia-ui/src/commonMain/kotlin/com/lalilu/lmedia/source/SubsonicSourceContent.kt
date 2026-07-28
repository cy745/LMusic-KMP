package com.lalilu.lmedia.source

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lalilu.common.ext.io
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceCard
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import kotlinx.coroutines.Dispatchers

fun SubsonicSource.subsonicSourceContent(
    modifier: Modifier = Modifier,
) = LazyStaggeredGridContent {
    val source by remember { source() }.collectAsState(
        initial = Snapshot.Empty,
        context = Dispatchers.io
    )

    return@LazyStaggeredGridContent {
        item(key = this@subsonicSourceContent.name) {
            SourceCard(
                modifier = modifier,
                state = { source }
            )
        }
    }
}

