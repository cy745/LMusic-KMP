package com.lalilu.lmedia.source

import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.repository.SourceStatus
import org.koin.compose.koinInject

fun RemoteSource.remoteSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val repository = koinInject<MediaSourceBindingRepository>()
    val syncState = state.collectAsStateWithLifecycle()
    val latestSnapshot = snapshot.collectAsStateWithLifecycle()
    val status = repository.observeSource(name).collectAsStateWithLifecycle(initialValue = null)

    return@LazyStaggeredGridContent {
        item(key = this@remoteSourceContent.name) {
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
