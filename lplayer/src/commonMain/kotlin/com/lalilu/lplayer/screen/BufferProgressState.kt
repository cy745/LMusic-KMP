package com.lalilu.lplayer.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.observeBufferProgress
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject

/**
 * 当前歌曲的缓冲进度（0f..1f），供进度条的次级层使用。
 *
 * 切歌时先归零：新歌还没开始下载，沿用上一首的比例会显示成"已经缓冲了一半"。
 * 数据源不上报（本地文件等）或无法判断总长度时保持 0f，进度条就不画次级层。
 */
@Composable
internal fun rememberBufferedFraction(
    currentItem: State<LAudio?>,
    platformSource: PlatformMediaSource = koinInject(),
): () -> Float {
    val fraction = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentItem, platformSource) {
        // collectLatest：切歌时立刻取消上一首的订阅，避免旧数据源继续往新歌的进度上写值
        snapshotFlow { currentItem.value }
            .collectLatest { audio ->
                fraction.floatValue = 0f
                if (audio == null) return@collectLatest

                platformSource.observeBufferProgress(audio)
                    .collect { value -> fraction.floatValue = value ?: 0f }
            }
    }

    return remember(fraction) { { fraction.floatValue } }
}
