package com.lalilu.lmedia.rpc.impl

import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.rpc.RemotableMediaSource
import com.lalilu.lmedia.rpc.RemoteMediaSourceService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

object RemoteMediaSourceServiceImpl : RemoteMediaSourceService {
    val targetSource = MutableStateFlow<RemotableMediaSource?>(null)

    override suspend fun getName(): String? = "targetSource.value?.getName()"

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun source(): Flow<Snapshot> =
        targetSource.flatMapLatest { it?.source() ?: MutableStateFlow(Snapshot.Empty) }
}
