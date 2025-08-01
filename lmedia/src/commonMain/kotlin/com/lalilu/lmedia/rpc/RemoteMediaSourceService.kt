package com.lalilu.lmedia.rpc

import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc

@Rpc
interface RemoteMediaSourceService {
    suspend fun getName(): String?
    fun source(): Flow<Snapshot>
}
