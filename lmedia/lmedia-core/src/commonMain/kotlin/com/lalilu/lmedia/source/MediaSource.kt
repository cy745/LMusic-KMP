package com.lalilu.lmedia.source

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

/**
 * 媒体源数据
 */
@Serializable
sealed class MediaData {

    @Serializable
    data class Url(val url: String) : MediaData()

    @Serializable
    class Bytes(val bytes: ByteArray) : MediaData()
}

/**
 * 媒体数据源
 */
interface MediaDataSource {
    companion object {
        val Empty = object : MediaDataSource {}
    }

    /**
     * 获取歌词
     */
    suspend fun getLyric(song: LAudio): String? = null

    /**
     * 获取图片
     */
    suspend fun getPicture(song: LAudio): MediaData? = null

    /**
     * 获取媒体数据
     */
    suspend fun getMedia(song: LAudio): MediaData? = null
}

/**
 * 媒体源（没有状态的数据源，只有传入参数获取数据的逻辑）
 *
 * @property name 数据源名称，兼具唯一标识的作用
 */
interface MediaSource {
    /**
     * 数据源名称，兼具唯一标识的作用
     */
    val name: String

    /**
     * 媒体源配置
     */
    val config: MediaSourceConfig
        get() = MediaSourceConfig(key = name, name = name)

    /**
     * 媒体数据源
     */
    val dataSource: MediaDataSource
        get() = MediaDataSource.Empty

    /**
     * 媒体源的流
     * 未实现的情况不可使用 [kotlinx.coroutines.flow.emptyFlow] 占位
     * 会导致其他Flow使用combine合并该Flow时一直等待此Flow返回
     */
    fun source(): Flow<Snapshot> = flowOf(Snapshot.Empty)

    /**
     * 媒体源初始化
     */
    fun init() {}

    /**
     * 配置改变
     */
    fun onConfigChange() {}
}