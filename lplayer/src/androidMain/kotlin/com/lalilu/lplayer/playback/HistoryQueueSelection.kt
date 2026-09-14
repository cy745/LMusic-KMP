package com.lalilu.lplayer.playback

import androidx.media3.common.Player
import com.lalilu.lplayer.extensions.toMediaItem

/** Restore display identity and position together, without preparing an unreadable source. */
internal fun Player.restoreHistoryQueueSelection(state: QueueState, history: PlaybackHistory.HistorySnapshot?): Long {
    if (currentMediaItemIndex == state.index && mediaItemCount == state.list.size &&
        state.list.indices.all { getMediaItemAt(it).mediaId == state.list[it].playbackId }) {
        return currentPosition.coerceAtLeast(0L)
    }
    val currentId = currentMediaItem?.mediaId
    val nextId = state.currentItem()?.playbackId
    val sameOccurrence = currentId != null && currentId == nextId &&
        (0 until currentMediaItemIndex.coerceAtLeast(0)).count { getMediaItemAt(it).mediaId == currentId } ==
        state.list.take(state.index).count { it.playbackId == nextId }
    val position = when {
        sameOccurrence -> currentPosition.coerceAtLeast(0L)
        history != null && history.resolveQueue(state.list).currentIndex == state.index -> history.position
        else -> 0L
    }
    setMediaItems(state.list.map { it.toMediaItem() }, state.index, position)
    return position
}
