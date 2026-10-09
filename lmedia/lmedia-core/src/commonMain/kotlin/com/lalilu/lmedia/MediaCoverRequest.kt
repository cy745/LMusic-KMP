package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.mp.KoinPlatform

/**
 * 带数据源内容代次的歌曲封面请求。
 *
 * [generation] 变化会让 Compose/Coil 把它视为新的请求，即使歌曲本身没有发生任何字段变化。
 */
data class MediaCoverRequest(
    val audio: LAudio,
    val generation: Long,
)

/** LAudio 自动包装为内容代次感知的封面请求，其他类型保持原样。 */
@Composable
fun rememberMediaCoverRequest(data: Any?): Any? {
    val audio = data as? LAudio ?: return data
    val platformSource = remember {
        runCatching { KoinPlatform.getKoin().get<PlatformMediaSource>() }.getOrNull()
    }
    val source = remember(platformSource, audio.mediaSourceName) {
        platformSource?.findSource(audio.mediaSourceName)
    }
    val generation = if (source != null) {
        val contentState by source.contentState.collectAsState()
        contentState.generation
    } else {
        0L
    }

    return remember(audio, generation) {
        MediaCoverRequest(audio = audio, generation = generation)
    }
}

/**
 * [rememberMediaCoverRequest] 的 State 版本：返回的 [State] 实例在 `data` 不变时保持稳定，
 * 调用方可以把它当稳定参数往下传（并延迟到绘制/使用时再读 value），避免每次重组都换一个新对象
 * 而让下游无法跳过重组。非 LAudio 的 `data` 原样包进 State，并在 `data` 变化时同步更新。
 */
@Composable
fun rememberMediaCoverRequestState(data: Any?): State<Any?> {
    // 注意要带 key：否则 data 变化后 State 里还是第一次的那个值
    val audio = data as? LAudio ?: return remember(data) { mutableStateOf(data) }

    val platformSource = remember {
        runCatching { KoinPlatform.getKoin().get<PlatformMediaSource>() }.getOrNull()
    }
    val source = remember(platformSource, audio.mediaSourceName) {
        platformSource?.findSource(audio.mediaSourceName)
    }
    val generation = if (source != null) {
        val contentState by source.contentState.collectAsState()
        contentState.generation
    } else {
        0L
    }

    val state = remember {
        mutableStateOf(MediaCoverRequest(audio = audio, generation = generation))
    }

    LaunchedEffect(audio, generation) {
        if (state.value.generation != generation || state.value.audio != audio) {
            state.value = MediaCoverRequest(audio = audio, generation = generation)
        }
    }

    return state
}
