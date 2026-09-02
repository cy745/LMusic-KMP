package com.lalilu.lplayer.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import com.lalilu.common.ext.io
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
import com.lalilu.llyricview.LyricContent
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.awaitContentReady
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [ViewModel::class])
class PlayerViewModel(
    private val platformSource: PlatformMediaSource
) : ViewModel(), LifecycleEventObserver {
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.queue.currentItemFlow()
    val lyricContent = mutableStateOf<LyricContent>(LyricContent.Loading(null))

    val currentQueue = LPlayer.instance.queue.expandedItems
        .mapLatest { it.rearrange() }

    init {
        currentItem
            .flatMapLatest { audio ->
                val source = audio?.let { song ->
                    platformSource.sources.firstOrNull { it.name == song.mediaSourceName }
                }
                if (audio == null || source == null) {
                    flowOf(
                        LyricContent.Ready(key = audio?.id, items = emptyList()),
                    )
                } else {
                    source.contentState
                        .filter { it.isReady }
                        // 新歌词加载完成前保留旧文档；LyricLayout 会在 position 的媒体身份改变后
                        // 冻结旧页面，加载完成后再直接执行两份完整歌词之间的过渡。
                        .mapLatest { contentState ->
                            LyricContent.Ready(
                                key = audio.id,
                                generation = contentState.generation,
                                items = retrieveLyric(audio),
                            )
                        }
                }
            }
            .onEach { lyricContent.value = it }
            .launchIn(viewModelScope)
    }

    suspend fun retrieveLyric(audio: LAudio?): List<LyricItem> = withContext(Dispatchers.io) {
        val song = audio ?: return@withContext emptyList()
        val source = platformSource.sources.firstOrNull { it.name == song.mediaSourceName }
            ?: return@withContext emptyList()
        source.awaitContentReady()
        val lyric = source.dataSource
            .runCatching { getLyric(song) }
            .getOrNull()

        return@withContext LyricUtils.parseLrc(lyric)
            ?: emptyList()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {}
            Lifecycle.Event.ON_START -> {}
            else -> {}
        }
    }
}
