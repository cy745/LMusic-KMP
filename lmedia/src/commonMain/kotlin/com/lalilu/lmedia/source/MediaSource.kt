package com.lalilu.lmedia.source

import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow

/**
 * 媒体数据源
 *
 * @property name 数据源名称
 */
interface MediaSource {
    val name: String

    /**
     * 媒体数据源的流
     */
    fun source(): Flow<Snapshot>
}