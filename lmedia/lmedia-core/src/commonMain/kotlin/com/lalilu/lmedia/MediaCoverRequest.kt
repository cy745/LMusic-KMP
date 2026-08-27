package com.lalilu.lmedia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        platformSource?.sources?.firstOrNull { it.name == audio.mediaSourceName }
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
