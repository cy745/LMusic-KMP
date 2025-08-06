package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.combineToOne
import com.lalilu.lmedia.source.Library
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Single(binds = [Library::class], createdAtStart = true)
class LMedia : Library(), KoinComponent {
    private val platformSource by inject<PlatformMediaSource>()

    init {
        instance = this
    }

    override fun snapshotFlow(): Flow<Snapshot> = snapshotFlow
    private val snapshotFlow: Flow<Snapshot> by lazy {
        combine(
            flows = platformSource.sources.map { it.source() },
            transform = { it.combineToOne() }
        )
    }

    companion object {
        lateinit var instance: LMedia
            private set
    }
}