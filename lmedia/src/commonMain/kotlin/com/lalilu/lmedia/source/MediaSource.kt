package com.lalilu.lmedia.source

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        Text("$name not implemented")
    }
}