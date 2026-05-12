package com.lalilu.lplayer

import com.lalilu.common.ext.io
import com.lalilu.lplayer.playback.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

object HistoryRecover : CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io

    fun recover(block: (ids: List<String>, index: Int, position: Long) -> Unit) {
        val id = LPlayerKV.historyPlayId.value
        val ids = LPlayerKV.historyPlaylistIds.value
        val position = LPlayerKV.historyPlayPosition.value
        val index = ids.indexOf(id).coerceAtLeast(0)
        block(ids, index, position)
    }

    fun startRecord(playback: Playback) {
        playback.isPlaying.onEach { isPlaying -> saveCurrentPosition(isPlaying, playback) }
            .launchIn(this)

        playback.queue.expandedItems
            .onEach { state ->
                LPlayerKV.historyPlayId.value = state.currentItem()?.idValue() ?: ""
                LPlayerKV.historyPlaylistIds.value = state.list.map { it.idValue() }
            }.launchIn(this)
    }

    private suspend fun saveCurrentPosition(isPlaying: Boolean, playback: Playback) {
        while (isPlaying && isActive) {
            LPlayerKV.historyPlayPosition.value = playback.currentPosition()
            delay(1000.milliseconds)
        }
    }
}