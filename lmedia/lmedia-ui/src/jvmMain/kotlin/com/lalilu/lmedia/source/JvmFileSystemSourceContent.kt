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
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch

fun JvmFileSystemSource.jvmFileSystemSourceContent(modifier: Modifier) = LazyStaggeredGridContent {
    val scope = rememberCoroutineScope()
    val launcher = rememberDirectoryPickerLauncher { directory ->
        directory ?: return@rememberDirectoryPickerLauncher
        scope.launch {
            selectDirectory(directory.bookmarkData().bytes.decodeToString())
        }
    }

    return@LazyStaggeredGridContent {
        item(key = this@jvmFileSystemSourceContent.name) {
            val directory = config.value.directoryBookmark
            SourcePipelineCard(
                modifier = modifier,
                title = "本地音乐文件夹",
                description = "扫描电脑上的音乐目录，并直接读取文件内的媒体信息",
                idleLabel = if (directory.isBlank()) "未选择目录" else "待扫描",
            ) { uiState ->
                if (directory.isNotBlank()) {
                    SourceInfoPanel(
                        modifier = Modifier.padding(top = 14.dp),
                        label = "当前扫描目录",
                        value = directory,
                    )
                }

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
