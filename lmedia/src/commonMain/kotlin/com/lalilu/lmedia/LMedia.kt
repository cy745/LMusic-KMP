package com.lalilu.lmedia

import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.combineToOne
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object LMedia : KoinComponent {
    val platformSource by inject<PlatformMediaSource>()

    val library: Flow<Snapshot> by lazy {
        combine(
            flows = platformSource.sources.map { it.source() },
            transform = { it.combineToOne() }
        )
    }
}