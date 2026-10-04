package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.BufferedRange
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.observeBufferProgress
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

/**
 * 当前歌曲已缓冲的区间，供进度条的次级层使用。
 *
 * 切歌时先清空：新歌还没开始下载，沿用上一首的区间会显示成"这段已经缓冲好了"。
 * 数据源不上报（本地文件等）或无法判断总长度时保持空列表，进度条就不画次级层。
 */
@Composable
internal fun rememberBufferedRanges(
    currentItem: State<LAudio?>,
    platformSource: PlatformMediaSource = koinInject(),
): () -> List<BufferedRange> {
    val ranges = remember { mutableStateOf<List<BufferedRange>>(emptyList()) }

    LaunchedEffect(currentItem, platformSource) {
        // collectLatest：切歌时立刻取消上一首的订阅，避免旧数据源继续往新歌的进度上写值
        snapshotFlow { currentItem.value }
            .collectLatest { audio ->
                ranges.value = emptyList()
                if (audio == null) return@collectLatest

                platformSource.observeBufferProgress(audio)
                    .collect { value -> ranges.value = value }
            }
    }

    return remember(ranges) { { ranges.value } }
}
