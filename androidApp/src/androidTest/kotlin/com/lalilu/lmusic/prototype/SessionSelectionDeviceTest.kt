package com.lalilu.lmusic.prototype

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.core.CoreSessionPlayer
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SessionSelectionDeviceTest {
    @Test fun startIntentTravelsWithQueueAndInstantFailureIsNotRetriedTwice() = runBlocking {
        val parent = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val attempted = mutableListOf<TrackKey>()
        val tracks = (0..2).map { TrackKey("selection-test", "$it") }
        var player: CoreSessionPlayer? = null
        try {
            val core = withContext(Dispatchers.Main) {
                val runtime = Media3CoreRuntime(InstrumentationRegistry.getInstrumentation().targetContext)
                PlaybackCore(parent, ContentResolver { key -> attempted += key; error("controlled missing content") }, runtime).also {
                    player = CoreSessionPlayer(it, runtime, parent, mutableMapOf())
                }
            }
            withContext(Dispatchers.Main) {
                val items = tracks.mapIndexed { index, key ->
                    MediaItem.Builder().setMediaId(MediaKey(key.source, key.id).stableKey)
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(Bundle().apply {
                            if (index == 0) putBoolean("com.lalilu.lplayer.start_queue_playback", true)
                        }).build()).build()
                }
                player!!.setMediaItems(items, 0, 0)
            }
            withTimeout(5000) { core.state.first { it.phase == Phase.Failed && it.intent == PlayIntent.Pause } }
            withContext(Dispatchers.Main) {
                assertEquals(tracks, attempted)
                assertFalse(player!!.getMediaItemAt(0).requestMetadata.extras?.getBoolean("com.lalilu.lplayer.start_queue_playback") == true)
            }
        } finally { withContext(Dispatchers.Main) { player?.release(); parent.cancel() } }
    }
}
