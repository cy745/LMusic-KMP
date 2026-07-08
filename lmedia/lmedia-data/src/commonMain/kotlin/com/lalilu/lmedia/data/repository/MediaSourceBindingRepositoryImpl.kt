package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.Snapshot as DomainSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import com.lalilu.common.ext.io

/**
 * Auto-starts MediaSource → database binding on creation.
 * Sources implement [MediaSource] which produces [DomainSnapshot].
 */
@Single(binds = [MediaSourceBindingRepository::class], createdAtStart = true)
class MediaSourceBindingRepositoryImpl(
    private val platformSource: PlatformMediaSource,
    private val database: ILMediaDatabase
) : MediaSourceBindingRepository {
    private val scope = CoroutineScope(Dispatchers.io + SupervisorJob())

    init {
        scope.launch { startBinding() }
    }

    override fun getSources(): PlatformMediaSource = platformSource

    override suspend fun startBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest { snapshot ->
                database.mediaDao().insert(snapshot = snapshot, sourceName = source.name)
            }.launchIn(scope)
        }
    }
}
