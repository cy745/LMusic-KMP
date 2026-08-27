package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.StateFlow

/**
 * 有状态的媒体数据源。
 *
 * 数据源负责驱动自己的扫描流程和运行状态，并只在得到完整结果后更新 [snapshot]；数据库聚合层
 * 独立消费每个数据源的最新结果，彼此之间不形成全局等待关系。
 *
 * @property name 数据源的唯一标识，也是数据库判断歌曲归属的依据。
 */
interface MediaSource {
    val name: String

    val dataSource: MediaDataSource
        get() = MediaDataSource.Empty

    /** 当前任务的运行状态。 */
    val state: StateFlow<SnapshotState>

    /** 最近一次完整成功结果；Loading、失败和取消不会清空它。 */
    val snapshot: StateFlow<Snapshot?>

    fun init() {}
    fun onConfigChange() {}
}
