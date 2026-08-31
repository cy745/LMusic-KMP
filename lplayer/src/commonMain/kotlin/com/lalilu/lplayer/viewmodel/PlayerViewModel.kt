package com.lalilu.lplayer.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import com.lalilu.common.ext.io
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
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
import kotlinx.coroutines.flow.map
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
    val lyricItems = mutableStateOf<List<LyricItem>>(emptyList())

    val currentQueue = LPlayer.instance.queue.expandedItems
        .mapLatest { it.rearrange() }

    init {
        currentItem
            .onEach { lyricItems.value = emptyList() }
            .flatMapLatest { audio ->
                val source = audio?.let { song ->
                    platformSource.sources.firstOrNull { it.name == song.mediaSourceName }
                }
                if (audio == null || source == null) {
                    flowOf(null)
                } else {
                    source.contentState
                        .filter { it.isReady }
                        // generation 每次成功加载都会变化，因此同一首歌曲也会再次触发歌词读取。
                        .map { audio }
                }
            }
            .mapLatest(::retrieveLyric)
            .onEach { lyricItems.value = it }
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
