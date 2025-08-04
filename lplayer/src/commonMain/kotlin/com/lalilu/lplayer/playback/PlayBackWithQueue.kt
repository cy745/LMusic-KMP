package com.lalilu.lplayer.playback

import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGroupItem
import com.lalilu.lmedia.entity.LItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
abstract class PlayBackWithQueue() : Playback, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io

    protected val currentItemIndex = MutableStateFlow(0)
    protected val playlistFlow = MutableStateFlow<List<LItem>>(emptyList())

    protected val flattenPlaylistFlow = playlistFlow.flatten()
        .stateIn(this, SharingStarted.WhileSubscribed(), emptyList())

    protected val currentItemFlow = flattenPlaylistFlow
        .combine(currentItemIndex) { list, index -> list.getOrNull(index) }
        .stateIn(this, SharingStarted.WhileSubscribed(), null)

    override fun playlist(): Flow<List<LItem>> = playlistFlow
    override fun flattenPlaylist(): StateFlow<List<LAudio>> = flattenPlaylistFlow
    override fun currentItem(): StateFlow<LAudio?> = currentItemFlow
    override fun currentItemIndex(): StateFlow<Int> = currentItemIndex

    override fun updatePlaylist(playlist: List<LItem>) {
        launch { playlistFlow.emit(playlist) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<List<LItem>>.flatten(): Flow<List<LAudio>> {
    return mapLatest { list ->
        list.flatMap {
            when (it) {
                is LGroupItem -> it.items
                is LAudio -> listOf(it)
                else -> emptyList()
            }
        }
    }
}
