package com.lalilu.lplayer.extensions

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.lalilu.lplayer.playback.SurpriseQueueOrder
import com.lalilu.lplayer.playback.PlayNextRequests
import com.lalilu.lplayer.playback.PlaybackDirection
import com.lalilu.lmedia.domain.model.MediaKey

@OptIn(UnstableApi::class)
internal class QueueControlPlayer(
    player: ExoPlayer,
    private val onNavigate: (PlaybackDirection) -> Unit,
    private val onCancelNavigation: () -> Unit,
    private val onPausePreparation: () -> Unit,
    private val onStopPreparation: () -> Unit,
) : ForwardingPlayer(player), Player.Listener {
    private val requestedNext = PlayNextRequests()

    fun requestPlayNext(playbackId: String) {
        val key = requireNotNull(MediaKey.parse(playbackId))
        requestedNext.enqueue(key)
    }

    private fun retainExistingRequests() {
        requestedNext.retain((0 until mediaItemCount)
            .mapNotNull { MediaKey.parse(getMediaItemAt(it).mediaId) }.toSet())
    }

    override fun removeMediaItem(index: Int) {
        super.removeMediaItem(index)
        retainExistingRequests()
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        super.removeMediaItems(fromIndex, toIndex)
        retainExistingRequests()
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        super.replaceMediaItem(index, mediaItem)
        retainExistingRequests()
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        super.replaceMediaItems(fromIndex, toIndex, mediaItems)
        retainExistingRequests()
    }

    override fun clearMediaItems() {
        super.clearMediaItems()
        requestedNext.clear()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        super.setMediaItems(mediaItems)
        requestedNext.clear()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        requestedNext.clear()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        requestedNext.clear()
    }

    private fun playRequestedNext(afterAutomaticTransition: Boolean = false): Boolean {
        val items = (0 until currentTimeline.windowCount).map {
            currentTimeline.getWindow(it, Timeline.Window()).mediaItem
        }
        val key = requestedNext.take(items.mapNotNull { MediaKey.parse(it.mediaId) }.toSet()) ?: return false
        var index = items.indexOfFirst { it.mediaId == key.stableKey }
        if (afterAutomaticTransition && index == currentMediaItemIndex) {
            // The engine already selected the requested song. Do not restart its position.
            tryMoveNext()
            return true
        }
        if (playMode == PlayMode.Shuffle && index != currentMediaItemIndex) {
            // AUTO has already moved to the slot before the old current. Replace that slot,
            // not the slot before the new current, to retain the previously played song below it.
            val next = if (afterAutomaticTransition) currentMediaItemIndex
            else SurpriseQueueOrder.nextIndex(items.size, currentMediaItemIndex)
            // This pair is an internal permutation, not a removal. Bypass external edit pruning
            // while one item is transiently absent between the two native replacements.
            super.replaceMediaItem(index, items[next])
            super.replaceMediaItem(next, items[index])
            index = next
        }
        seekTo(index, 0)
        // Replacing AUTO's current slot may not emit a SEEK item transition (same index).
        if (afterAutomaticTransition) tryMoveNext()
        return true
    }

    init {
        player.addListener(this)
        player.shuffleOrder = CustomShuffleOrder(0)
    }

    private fun tryMoveNext() {
        if (playMode == PlayMode.Shuffle) {
            val target = getRandomNextIndex()
            if (target < 0) return

            val targetMediaItem = currentTimeline
                .getWindow(target, Timeline.Window())
                .mediaItem
            val nextMediaItem = currentTimeline
                .getWindow(nextMediaItemIndex, Timeline.Window())
                .mediaItem
            super.replaceMediaItem(target, nextMediaItem)
            super.replaceMediaItem(nextMediaItemIndex, targetMediaItem)
        }
    }

    private fun getRandomNextIndex(): Int {
        return SurpriseQueueOrder.candidateIndex(currentTimeline.windowCount, currentMediaItemIndex)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if ((reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            && playRequestedNext(afterAutomaticTransition = true)) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            tryMoveNext()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // Sequential playback has no item transition after its last item. Consume an explicit
        // next request here too, but never revive playback after a pause or a newer state change.
        if (playbackState != Player.STATE_ENDED || this.playbackState != Player.STATE_ENDED || !playWhenReady) return
        if (playRequestedNext()) prepare()
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        super.setShuffleModeEnabled(shuffleModeEnabled)
        tryMoveNext()
    }

    override fun seekToNext() {
        onNavigate(PlaybackDirection.Forward)
        if (playRequestedNext()) return
        tryMoveNext()
        super.seekToNext()
    }

    override fun seekToPrevious() {
        onNavigate(PlaybackDirection.Backward)
        super.seekToPrevious()
        tryMoveNext()
    }

    override fun seekToNextMediaItem() {
        onNavigate(PlaybackDirection.Forward)
        if (playRequestedNext()) return
        tryMoveNext()
        super.seekToNextMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        onNavigate(PlaybackDirection.Backward)
        super.seekToPreviousMediaItem()
        tryMoveNext()
    }

    override fun pause() {
        onPausePreparation()
        onCancelNavigation()
        super.pause()
    }

    override fun stop() {
        onStopPreparation()
        onCancelNavigation()
        super.stop()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!playWhenReady) onCancelNavigation()
        super.setPlayWhenReady(playWhenReady)
    }

    @UnstableApi
    private class CustomShuffleOrder(private val size: Int) : ShuffleOrder {
        override fun getLength(): Int {
            return size
        }

        override fun getNextIndex(index: Int): Int {
            return SurpriseQueueOrder.nextIndex(size, index)
        }

        override fun getPreviousIndex(index: Int): Int {
            return SurpriseQueueOrder.previousIndex(size, index)
        }

        override fun getLastIndex(): Int {
            return if (size > 0) size - 1 else C.INDEX_UNSET
        }

        override fun getFirstIndex(): Int {
            return if (size > 0) 0 else C.INDEX_UNSET
        }

        override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
            return CustomShuffleOrder(length + insertionCount)
        }

        override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
            return CustomShuffleOrder(length - indexToExclusive + indexFrom)
        }

        override fun cloneAndClear(): ShuffleOrder {
            return CustomShuffleOrder(0)
        }
    }
}


@OptIn(UnstableApi::class)
internal fun ExoPlayer.setUpQueueControl(
    onNavigate: (PlaybackDirection) -> Unit,
    onCancelNavigation: () -> Unit,
    onPausePreparation: () -> Unit,
    onStopPreparation: () -> Unit,
): Player {
    return QueueControlPlayer(this, onNavigate, onCancelNavigation, onPausePreparation, onStopPreparation)
}
