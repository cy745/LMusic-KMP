package com.lalilu.lplayer.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.LyricUtils
import com.lalilu.lmedia.LMedia
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lplayer.LPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class PlayerViewModel(
    private val platformSource: PlatformMediaSource
) : ViewModel(), LifecycleEventObserver {
    val isPlaying = LPlayer.instance.isPlaying
    val currentItem = LPlayer.instance.currentItem
    val currentTime = mutableStateOf(0L)
    val lyricItems = mutableStateOf<List<LyricItem>>(emptyList())

    init {
        LMedia.instance.whenReady {
            viewModelScope.launch {
                val list = LMedia.instance.get<LAudio>()
                LPlayer.instance.updatePlaylist(list)
                Logger.i("[LPlayer] set list: ${list.size}")
            }
        }

        LPlayer.instance.currentItem
            .onEach { lyricItems.value = retrieveLyric(it) }
            .launchIn(viewModelScope)
    }

    suspend fun retrieveLyric(audio: LAudio?): List<LyricItem> = withContext(Dispatchers.io) {
        val song = audio ?: return@withContext emptyList()
        val lyric = platformSource.sources.firstOrNull { it.name == song.mediaSourceName }
            ?.dataSource
            ?.runCatching { getLyric(song) }
            ?.getOrNull()

        return@withContext LyricUtils.parseLrc(lyric)
            ?: emptyList()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
            }

            Lifecycle.Event.ON_START -> {
            }

            else -> {}
        }
    }
}