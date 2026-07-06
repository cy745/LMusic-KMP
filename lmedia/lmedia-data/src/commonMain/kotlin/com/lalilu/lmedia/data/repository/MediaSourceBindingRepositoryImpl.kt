package com.lalilu.lmedia.data.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import org.koin.core.annotation.Single

@Single(binds = [MediaSourceBindingRepository::class])
class MediaSourceBindingRepositoryImpl(
    private val platformSource: PlatformMediaSource,
    private val database: ILMediaDatabase
) : MediaSourceBindingRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun getSources(): PlatformMediaSource = platformSource

    override suspend fun startBinding() {
        platformSource.sources.forEach { source ->
            source.source().mapLatest { snapshot ->
                database.mediaDao().insert(snapshot = snapshot, sourceName = source.name)
            }.launchIn(scope)
        }
    }
}
