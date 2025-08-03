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

    /**
     * 获取歌词
     */
    fun requireLyricFlow(id: String, type: String): Flow<String?>

    /**
     * 获取媒体数据
     */
    fun requireMediaFlow(id: String, type: String): Flow<ByteArray?>

    /**
     * 获取图片数据
     */
    fun requirePictureFlow(id: String, type: String): Flow<ByteArray?>
}