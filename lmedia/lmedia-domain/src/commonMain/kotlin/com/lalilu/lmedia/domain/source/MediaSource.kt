package com.lalilu.lmedia.domain.source

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Media source — stateless data provider.
 *
 * @property name Unique identifier for this source.
 */
interface MediaSource {
    val name: String

    val dataSource: MediaDataSource
        get() = MediaDataSource.Empty

    fun source(): Flow<Snapshot> = flowOf(Snapshot.Empty)

    fun init() {}
    fun onConfigChange() {}
}
