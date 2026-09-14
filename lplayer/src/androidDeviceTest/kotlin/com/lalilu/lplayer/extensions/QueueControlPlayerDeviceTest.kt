package com.lalilu.lplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lplayer.playback.PlaybackHistory
import com.lalilu.lplayer.playback.QueueState
import com.lalilu.lplayer.playback.restoreHistoryQueueSelection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs the production wrapper against Media3, including its synchronous timeline callbacks. */
@androidx.annotation.OptIn(UnstableApi::class)
class QueueControlPlayerDeviceTest {
    @Test
    fun identicalHistoryRefreshDoesNotResetPositionOrPendingNext() = withPlayer { player ->
        val songs = (0..3).map { LAudio(id = "$it", mediaSourceName = "not-ready-source") }
        val history = PlaybackHistory.HistorySnapshot(songs.map { it.id }, 2, 12000,
            songs.map { it.mediaSourceName })
        val state = QueueState(songs, 2)
        player.restoreHistoryQueueSelection(state, history)
        player.seekTo(19000)
        player.requestPlayNext(songs[0].playbackId)
        player.restoreHistoryQueueSelection(state, history)
        assertEquals(19000L, player.currentPosition)
        player.seekToNextMediaItem()
        assertEquals(songs[0].playbackId, player.currentMediaItem?.mediaId)
    }

    @Test
    fun historySelectionIsCorrectBeforeAnySourceCanBeOpened() = withPlayer { player ->
        val songs = (0..3).map { LAudio(id = "$it", mediaSourceName = "not-ready-source") }
        val history = PlaybackHistory.HistorySnapshot(songs.map { it.id }, 2, 12000,
            songs.map { it.mediaSourceName })
        val state = QueueState(songs, 2)
        player.restoreHistoryQueueSelection(state, history)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(songs[2].playbackId, player.currentMediaItem?.mediaId)
        assertEquals(12000L, player.currentPosition)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        val mirrored = QueueState(songs, player.currentMediaItemIndex)
        assertEquals(state.rearrange(), mirrored.rearrange())
    }

    @Test
    fun fillingEarlierHistorySlotsKeepsCurrentProgress() = withPlayer { player ->
        val songs = (0..3).map { LAudio(id = "$it", mediaSourceName = "not-ready-source") }
        val history = PlaybackHistory.HistorySnapshot(songs.map { it.id }, 2, 12000,
            songs.map { it.mediaSourceName })
        player.restoreHistoryQueueSelection(QueueState(songs.drop(1), 1), history)
        player.seekTo(19000)
        player.restoreHistoryQueueSelection(QueueState(songs, 2), history)
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals(19000L, player.currentPosition)
        assertEquals(Player.STATE_IDLE, player.playbackState)
    }

    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(MediaKey("device-test", id).stableKey)
        .setUri("file:///unused-$id.wav")
        .build()

    private fun withPlayer(test: (QueueControlPlayer) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val engine = ExoPlayer.Builder(instrumentation.targetContext).build()
            val player = QueueControlPlayer(engine, {}, {}, {}, {})
            try {
                test(player)
            } catch (error: Throwable) {
                failure = error
            } finally {
                player.release()
            }
        }
        failure?.let { throw it }
    }

    @Test
    fun explicitNextOverridesNaturalSuccessor() = withPlayer { player ->
        val items = listOf("a", "b", "c", "d").map(::item)
        player.setMediaItems(items, 0, 0)
        player.playMode = PlayMode.Sequential
        player.requestPlayNext(items[3].mediaId)
        player.seekToNextMediaItem()
        assertEquals(items[3].mediaId, player.currentMediaItem?.mediaId)
    }

    @Test
    fun shuffleExplicitNextKeepsPreviouslyPlayingSongImmediatelyAfterIt() = withPlayer { player ->
        val items = listOf("a", "b", "c", "d").map(::item)
        player.setMediaItems(items, 2, 0)
        player.playMode = PlayMode.Shuffle
        player.requestPlayNext(items[0].mediaId)
        player.seekToNextMediaItem()
        assertEquals(items[0].mediaId, player.currentMediaItem?.mediaId)
        assertEquals(items[2].mediaId, player.getMediaItemAt(2).mediaId)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(items.map { it.mediaId }.sorted(),
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.sorted())
    }

    @Test
    fun removingRequestedSongDoesNotReviveRequestWhenSongIsAddedAgain() = withPlayer { player ->
        val items = listOf("a", "b", "c").map(::item)
        player.setMediaItems(items, 0, 0)
        player.playMode = PlayMode.Sequential
        player.requestPlayNext(items[2].mediaId)
        player.removeMediaItem(2)
        player.addMediaItem(items[2])
        player.seekToNextMediaItem()
        assertEquals(items[1].mediaId, player.currentMediaItem?.mediaId)
    }

    @Test
    fun replacingRequestedSongThroughBatchEditDropsOldRequest() = withPlayer { player ->
        val items = listOf("a", "b", "c").map(::item)
        player.setMediaItems(items, 0, 0)
        player.requestPlayNext(items[2].mediaId)
        player.replaceMediaItems(2, 3, listOf(item("d")))
        player.addMediaItem(items[2])
        player.seekToNextMediaItem()
        assertEquals(items[1].mediaId, player.currentMediaItem?.mediaId)
    }

    @Test
    fun replacingWholeQueueClearsRequestsEvenWhenSongStillExists() = withPlayer { player ->
        val items = listOf("a", "b", "c").map(::item)
        player.setMediaItems(items, 0, 0)
        player.requestPlayNext(items[2].mediaId)
        player.setMediaItems(items, 0, 0)
        player.seekToNextMediaItem()
        assertEquals(items[1].mediaId, player.currentMediaItem?.mediaId)
    }

    @Test
    fun shuffleInternalPermutationsKeepBothExplicitRequests() = withPlayer { player ->
        val items = listOf("a", "b", "c", "d", "e").map(::item)
        player.setMediaItems(items, 3, 0)
        player.playMode = PlayMode.Shuffle
        player.requestPlayNext(items[0].mediaId)
        player.requestPlayNext(items[1].mediaId)
        player.seekToNextMediaItem()
        assertEquals(items[1].mediaId, player.currentMediaItem?.mediaId)
        player.seekToNextMediaItem()
        assertEquals(items[0].mediaId, player.currentMediaItem?.mediaId)
    }
}
