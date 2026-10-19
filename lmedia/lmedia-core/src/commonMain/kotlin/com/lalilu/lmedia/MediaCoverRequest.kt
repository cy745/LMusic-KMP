package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import org.koin.mp.KoinPlatform

/**
 * 带数据源内容代次的歌曲封面请求。
 *
 * [generation] 变化会让 Compose/Coil 把它视为新的请求，即使歌曲本身没有发生任何字段变化。
 *
 * 身份只由「哪一首 + 代次」决定，**不包含歌曲对象本身**：元数据补丁会换掉 [LAudio] 快照
 * （多了专辑、时长），而封面并没有变。如果对象跟着换，下游就会把它当成新请求重新加载一遍，
 * 多出来的那次加载会把正在进行的封面过渡打断——旧图被移除、新图从完全透明重新淡入，
 * 看起来就是"旧封面闪掉、新封面突然出现"。
 */
class MediaCoverRequest(
    audio: LAudio,
    val generation: Long,
) {
    /** 最近一次的歌曲快照：补丁只更新它，不改变请求身份。 */
    var audio: LAudio = audio
        internal set

    val sourceName: String get() = audio.mediaSourceName
    val audioId: String get() = audio.id

    override fun equals(other: Any?): Boolean =
        other is MediaCoverRequest &&
            other.sourceName == sourceName &&
            other.audioId == audioId &&
            other.generation == generation

    override fun hashCode(): Int {
        var result = sourceName.hashCode()
        result = 31 * result + audioId.hashCode()
        result = 31 * result + generation.hashCode()
        return result
    }

    override fun toString(): String = "MediaCoverRequest($sourceName/$audioId, generation=$generation)"
}

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

    // 只按「哪一首 + 代次」记忆：补丁换掉 audio 对象时不重新发请求，只更新快照
    val request = remember(audio.mediaSourceName, audio.id, generation) {
        MediaCoverRequest(audio = audio, generation = generation)
    }
    SideEffect { request.audio = audio }
    return request
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

    // 同样只按身份换请求：补丁只更新快照，避免下游把同一张封面重新加载一次
    LaunchedEffect(audio.mediaSourceName, audio.id, generation) {
        state.value = MediaCoverRequest(audio = audio, generation = generation)
    }
    SideEffect { state.value.audio = audio }

    return state
}
