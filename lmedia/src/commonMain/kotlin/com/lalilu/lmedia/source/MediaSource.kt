package com.lalilu.lmedia.source

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lalilu.lmedia.entity.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 媒体数据源
 *
 * @property name 数据源名称
 */
interface MediaSource {
    val name: String

    /**
     * 媒体数据源的流
     * 未实现的情况不可使用 [kotlinx.coroutines.flow.emptyFlow] 占位
     * 会导致其他Flow使用combine合并该Flow时一直等待此Flow返回
     */
    fun source(): Flow<Snapshot> = flowOf(Snapshot.Empty)

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        Text("$name not implemented")
    }
}