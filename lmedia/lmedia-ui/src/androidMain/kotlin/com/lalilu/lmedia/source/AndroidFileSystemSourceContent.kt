package com.lalilu.lmedia.source

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyStaggeredGridContent
import com.lalilu.lmedia.component.SourceActionButton
import com.lalilu.lmedia.component.SourceActionStyle
import com.lalilu.lmedia.component.SourceInfoPanel
import com.lalilu.lmedia.component.SourcePipelineCard
import com.lalilu.lmedia.source.filesystem.AndroidFileSystemSource
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch

fun AndroidFileSystemSource.androidFileSystemSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val scope = rememberCoroutineScope()
    val launcher = rememberDirectoryPickerLauncher { directory ->
        directory ?: return@rememberDirectoryPickerLauncher
        scope.launch {
            selectDirectory(directory.bookmarkData().bytes.decodeToString())
        }
    }

    return@LazyStaggeredGridContent {
        item(key = this@androidFileSystemSourceContent.name) {
            val directory = config.value.directoryBookmark
            SourcePipelineCard(
                modifier = modifier,
                title = "本地音乐文件夹",
                description = "直接扫描所选目录，封面与歌词均从原始文件读取",
                idleLabel = if (directory.isBlank()) "未选择目录" else "待扫描",
            ) { uiState ->
                SourceInfoPanel(
                    modifier = Modifier.padding(top = 14.dp),
                    label = "当前扫描目录",
                    value = directory.ifBlank { "尚未选择音乐文件夹" },
                    supportingText = if (directory.isBlank()) "选择后会立即进行第一次扫描" else null,
                    emphasized = directory.isBlank(),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.isLoading) {
                        SourceActionButton(
                            title = "停止扫描",
                            style = SourceActionStyle.Primary,
                            onClick = ::cancel,
                        )
                    } else if (directory.isBlank()) {
                        SourceActionButton(
                            title = "选择音乐文件夹",
                            style = SourceActionStyle.Primary,
                            onClick = { launcher.launch() },
                        )
                    } else {
                        SourceActionButton(
                            title = "重新扫描",
                            style = SourceActionStyle.Primary,
                            onClick = ::refresh,
                        )
                        SourceActionButton(
                            title = "更换目录",
                            onClick = { launcher.launch() },
                        )
                    }
                }
            }
        }
    }
}
