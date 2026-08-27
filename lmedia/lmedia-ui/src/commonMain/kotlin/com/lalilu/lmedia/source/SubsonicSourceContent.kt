package com.lalilu.lmedia.source

import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SourceStatus
import com.lalilu.lmedia.source.subsonic.SubsonicSource
import org.koin.compose.koinInject

fun SubsonicSource.subsonicSourceContent(
    modifier: Modifier = Modifier,
) = LazyStaggeredGridContent {
    val repository = koinInject<MediaSourceBindingRepository>()
    val syncState = state.collectAsStateWithLifecycle()
    val latestSnapshot = snapshot.collectAsStateWithLifecycle()
    val status = repository.observeSource(name).collectAsStateWithLifecycle(initialValue = null)

    return@LazyStaggeredGridContent {
        item(key = this@subsonicSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                status = {
                    status.value ?: SourceStatus(
                        syncState = syncState.value,
                        resultRevision = latestSnapshot.value?.revision,
                        songCount = latestSnapshot.value?.audios?.size ?: 0,
                    )
                },
                snapshot = { latestSnapshot.value },
            )
        }
    }
}
