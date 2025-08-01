package com.lalilu.lmedia.rpc

import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow


/**
 * 定义一个数据源是可远程访问的
 */
interface RemotableMediaSource {
    /**
     * 获取数据源名称
     */
    suspend fun getName(): String

    /**
     * 获取数据源数据
     */
    fun source(): Flow<Snapshot>
}