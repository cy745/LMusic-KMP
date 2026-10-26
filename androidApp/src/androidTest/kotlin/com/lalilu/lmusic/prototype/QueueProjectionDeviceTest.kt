package com.lalilu.lmusic.prototype

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.core.CoreSessionPlayer
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QueueProjectionDeviceTest {
    @Test fun largeQueueProgressReusesTimelineAndMetadataChangesStillReachControllers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "queue-projection-${System.nanoTime()}")
        val file = PrototypeAudio.write(directory, "queue", durationMs=30000)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val backing = mutableMapOf<TrackKey, MediaItem>()
        var lookups = 0
        val items = object : MutableMap<TrackKey, MediaItem> by backing {
            override fun get(key: TrackKey): MediaItem? { lookups++; return backing[key] }
        }
        lateinit var runtime: Media3CoreRuntime
        lateinit var player: CoreSessionPlayer
        var ownedPlayer: CoreSessionPlayer? = null
        lateinit var core: PlaybackCore
        suspend fun waitFor(predicate: () -> Boolean) = withTimeout(15000) {
            while (!withContext(Dispatchers.Main) { predicate() }) delay(20)
        }
        try {
            withContext(Dispatchers.Main) {
                runtime = Media3CoreRuntime(context, gain=0f)
                core = PlaybackCore(scope, ContentResolver { PlayableResource(file.toURI().toString()) }, runtime)
                player = CoreSessionPlayer(core, runtime, scope, items)
                ownedPlayer = player
                player.setMediaItems((0 until 10000).map { index -> MediaItem.Builder()
                    .setMediaId(MediaKey("projection", "$index").stableKey)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $index").build()).build() },0,0)
            }
            waitFor { core.state.value.phase == Phase.ReadyPaused && player.duration == 30000L }
            val original = withContext(Dispatchers.Main) { player.currentTimeline }
            withContext(Dispatchers.Main) { lookups = 0 }
            val start = android.os.SystemClock.elapsedRealtime()
            repeat(100) { index ->
                withContext(Dispatchers.Main) {
                    runtime.onEvent?.invoke(RuntimeEvent.Position(core.state.value.ticket!!, index.toLong(), 30000, 30000))
                    yield(); yield()
                    player.currentPosition
                }
            }
            val calls = withContext(Dispatchers.Main) { lookups }
            println("QUEUE_PROJECTION size=10000 updates=100 itemLookups=$calls elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}")
            withContext(Dispatchers.Main) {
                assertSame("Progress alone must not allocate another full queue timeline", original, player.currentTimeline)
                assertTrue("Progress must not re-read all queue metadata", calls < 20000)
                player.replaceMediaItem(0, player.getMediaItemAt(0).buildUpon()
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Updated song").build()).build())
            }
            waitFor { player.mediaMetadata.title == "Updated song" }
            withContext(Dispatchers.Main) {
                player.setSessionArtwork(core.state.value.selected, byteArrayOf(1,2,3))
                assertArrayEquals(byteArrayOf(1,2,3), player.mediaMetadata.artworkData)
            }
            val external = MediaItem.Builder().setMediaId(MediaKey("projection", "outside").stableKey).build()
            withContext(Dispatchers.Main) {
                player.setMode(com.lalilu.lplayer.extensions.PlayMode.Shuffle).await()
                player.requestPlayNext(external).await()
                assertNotNull(backing[TrackKey("projection", "outside")])
                assertEquals(10000, player.mediaItemCount)
                player.seekToNextMediaItem()
            }
            waitFor { core.state.value.selected?.id == "outside" }
            withContext(Dispatchers.Main) { player.setMediaItems(listOf(external), 0, 0) }
            waitFor { player.mediaItemCount == 1 }
            // Allow the completed command to prune the old metadata after pending mutations settle.
            withContext(Dispatchers.Main) { yield(); yield() }
            assertEquals(setOf(TrackKey("projection", "outside")), withContext(Dispatchers.Main) { backing.keys.toSet() })
        } finally {
            withContext(Dispatchers.Main) { ownedPlayer?.release(); scope.cancel() }
            directory.deleteRecursively()
        }
    }
}
