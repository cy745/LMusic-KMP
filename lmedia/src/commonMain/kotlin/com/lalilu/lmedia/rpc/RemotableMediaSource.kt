package com.lalilu.lmedia.rpc

import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc


/**
 * 定义一个数据源是可远程访问的
 */
@Rpc
interface RemotableMediaSource {

    /**
     * 获取数据源数据
     */
    fun source(): Flow<Snapshot>

    /**
     * 获取数据源名称
     */
    suspend fun requireName(): String

    fun requireLyricFlow(id: String, type: String): Flow<String?>
    fun requireMediaFlow(id: String, type: String): Flow<ByteArray?>
    fun requirePictureFlow(id: String, type: String): Flow<ByteArray?>
}