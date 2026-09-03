package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.screen.SANDBOX_MEDIA_SOURCE_ROUTE
import com.lalilu.lmedia.source.sandbox.AndroidSandboxMediaSource
import com.lalilu.navigation.AppRouter

fun AndroidSandboxMediaSource.androidSandboxMediaSourceContent(
    modifier: Modifier,
) = LazyStaggeredGridContent {
    return@LazyStaggeredGridContent {
        item(key = this@androidSandboxMediaSourceContent.name) {
            SourcePipelineCard(
                modifier = modifier,
                title = "Sandbox 导入文件",
                description = "管理无法匹配到现有媒体源、由 LMusic 保存的音频副本",
                idleLabel = "待扫描",
            ) { uiState ->
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceActionButton(
                        title = if (uiState.isLoading) "停止扫描" else "重新扫描",
                        style = SourceActionStyle.Primary,
                        onClick = { if (uiState.isLoading) cancel() else refresh() },
                    )
                    SourceActionButton(
                        title = "编辑文件",
                        style = SourceActionStyle.Secondary,
                        enabled = !uiState.isLoading,
                        onClick = { AppRouter.route(SANDBOX_MEDIA_SOURCE_ROUTE).push() },
                    )
                }
            }
        }
    }
}
