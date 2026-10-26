package com.lalilu.lmusic.prototype

import android.content.ComponentName
import com.lalilu.lplayer.core.describe
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.service.MService
import com.lalilu.lplayer.extensions.toMediaItem
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.source.sandbox.SandboxMediaSource
import com.lalilu.lmedia.data.database.ILMediaDatabase
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.koin.mp.KoinPlatform
import java.io.File

/** Exercises the actual MService + MediaLibrarySession + application Playback entry, not a test-only service. */
class ProductionSessionDeviceTest {
    @Test fun appAndSystemControllerShareQueueIntentSeekAndMetadata()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val koin=KoinPlatform.getKoin()
        koin.get<MediaSourceBindingRepository>().startBinding()
        val source=koin.get<PlatformMediaSource>().sources.filterIsInstance<SandboxMediaSource>().single()
        val bindings=koin.get<MediaSourceBindingRepository>()
        val wasEnabled=koin.get<PlatformMediaSource>().isEnabled(source)
        val database=koin.get<ILMediaDatabase>()
        val directory=File(context.cacheDir,"p2-session-${System.nanoTime()}")
        val imported=mutableListOf<LAudio>()
        var activity: android.app.Activity?=null
        var browser:MediaBrowser?=null
        var original=emptyList<androidx.media3.common.MediaItem>()
        val originalOutputRequest = com.lalilu.lplayer.LPlayerKV.originalOutputRequested.value
        var oldIndex=0; var oldPosition=0L; var oldRepeat=Player.REPEAT_MODE_OFF; var oldShuffle=false
        suspend fun waitFor(label: String, predicate:()->Boolean) {
            try { withTimeout(20000) { while(!withContext(Dispatchers.Main) { predicate() }) delay(50) } }
            catch(error: TimeoutCancellationException) {
                val state=withContext(Dispatchers.Main) { browser?.let { "count=${it.mediaItemCount}, id=${it.currentMediaItem?.mediaId}, state=${it.playbackState}, intent=${it.playWhenReady}, position=${it.currentPosition}, error=${it.playerError}, app=${LPlayer.instance.queue.stateSnapshot()}" } }
                throw AssertionError("Timed out: $label; $state",error)
            }
        }
        try {
            assertTrue(bindings.setSourceEnabled(source.name,true))
            repeat(3) { index ->
                val file=PrototypeAudio.write(directory,"P2-${System.nanoTime()}-$index",durationMs=12000+index*1000)
                // RIFF INFO title makes UI and session metadata assertions independent of tag refresh timing.
                val title="P2 Track ${index+1}".toByteArray()+byteArrayOf(0)
                val padded=if(title.size%2==0) title else title+byteArrayOf(0)
                fun little(value:Int)=java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array()
                val info="INFOINAM".toByteArray()+little(title.size)+padded
                file.appendBytes("LIST".toByteArray()+little(info.size)+info)
                java.io.RandomAccessFile(file,"rw").use { it.seek(4); it.write(little((file.length()-8).toInt())) }
                val result=source.import(PlatformFile(file),emptyList())
                val audio=result.audio
                imported += audio
                database.mediaDao().upsertAudio(audio)
            }
            val instrumentation=InstrumentationRegistry.getInstrumentation()
            activity=instrumentation.startActivitySync(android.content.Intent(context,com.lalilu.lmusic.MainActivity::class.java)
                .setAction(android.content.Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse("lmusic://action/player_bottom_sheet?state=expanded"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val playback=LPlayer.instance
            browser=withContext(Dispatchers.Main) {
                MediaBrowser.Builder(context,SessionToken(context,ComponentName(context,MService::class.java))).buildAsync().await()
            }
            val controller=browser
            withContext(Dispatchers.Main) {
                original=(0 until controller.mediaItemCount).map(controller::getMediaItemAt)
                oldIndex=controller.currentMediaItemIndex; oldPosition=controller.currentPosition
                oldRepeat=controller.repeatMode; oldShuffle=controller.shuffleModeEnabled
                controller.pause()
            }
            playback.setPlaybackMode(com.lalilu.lplayer.playback.PlaybackMode.LOOP)
            waitFor("deterministic loop mode") { !controller.shuffleModeEnabled && controller.repeatMode==Player.REPEAT_MODE_ALL }
            playback.updatePlaylist(imported,0,false)
            waitFor("step 1") { controller.mediaItemCount==3 && controller.currentMediaItem?.mediaId==imported[0].playbackId && controller.playbackState==Player.STATE_READY }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            delay(700)
            val screen=instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screen)
            File(context.getExternalFilesDir(null),"p2-player-paused.png").outputStream().use { screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            screen.recycle()
            playback.play()
            waitFor("step 2") { controller.isPlaying && controller.currentPosition>100 }
            waitFor("actual AudioTrack format") { com.lalilu.lplayer.core.PlaybackOutput.capabilities.value.apiFormat != null }
            val output = com.lalilu.lplayer.core.PlaybackOutput.capabilities.value
            assertEquals(48000, output.apiFormat!!.sampleRate)
            assertEquals(com.lalilu.playback.core.Support.Unsupported, output.original)
            val beforeOutputRequest = withContext(Dispatchers.Main) { controller.currentPosition }
            withContext(Dispatchers.Main) { com.lalilu.lplayer.LPlayerKV.originalOutputRequested.value = true }
            delay(300)
            assertEquals(com.lalilu.playback.core.OutputMode.Experience,
                output.actualMode(com.lalilu.playback.core.OutputMode.Original))
            assertNotNull(output.fallbackReason(com.lalilu.playback.core.OutputMode.Original))
            assertTrue(withContext(Dispatchers.Main) { controller.isPlaying && controller.currentPosition > beforeOutputRequest })
            assertEquals(imported[0].playbackId, withContext(Dispatchers.Main) { controller.currentMediaItem?.mediaId })
            File(context.getExternalFilesDir(null), "p4-output-report.txt").writeText(
                output.describe(true))
            withContext(Dispatchers.Main) { controller.pause(); controller.seekTo(4000) }
            waitFor("step 3") { !controller.playWhenReady && controller.currentPosition>=3900 && controller.playbackState==Player.STATE_READY }
            assertEquals(imported[0].playbackId,playback.queue.currentItem()?.playbackId)
            playback.skipToNext()
            waitFor("step 4") { controller.currentMediaItem?.mediaId==imported[1].playbackId && controller.playbackState==Player.STATE_READY }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            withContext(Dispatchers.Main) { controller.play() }
            waitFor("step 5") { playback.isPlaying.value && playback.queue.currentItem()?.playbackId==imported[1].playbackId }
            withContext(Dispatchers.Main) { controller.seekToNext() }
            waitFor("step 6") { playback.queue.currentItem()?.playbackId==imported[2].playbackId && controller.isPlaying }
            assertEquals(playback.queue.currentItem()?.title,withContext(Dispatchers.Main) { controller.mediaMetadata.title.toString() })
            withContext(Dispatchers.Main) { controller.pause() }
            playback.seekTo(3000)
            waitFor("step 7") { controller.currentPosition>=2900 && controller.playbackState==Player.STATE_READY }
            assertFalse(playback.isPlaying.value)
            val selected=withContext(Dispatchers.Main) { controller.currentMediaItem?.mediaId }
            withContext(Dispatchers.Main) { controller.repeatMode=Player.REPEAT_MODE_ONE }
            waitFor("system repeat mode") { controller.repeatMode==Player.REPEAT_MODE_ONE && com.lalilu.lplayer.LPlayerKV.playMode.value==com.lalilu.lplayer.extensions.PlayMode.RepeatOne.name }
            playback.setPlaybackMode(com.lalilu.lplayer.playback.PlaybackMode.SHUFFLE)
            waitFor("application random mode") { controller.shuffleModeEnabled && controller.repeatMode==Player.REPEAT_MODE_ALL }
            assertEquals(selected,withContext(Dispatchers.Main) { controller.currentMediaItem?.mediaId })
            assertTrue(withContext(Dispatchers.Main) { controller.currentPosition>=2900 && !controller.playWhenReady })
            val physicalQueue=withContext(Dispatchers.Main) { (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId } }
            playback.playNext(imported[0])
            waitFor("pending first") { playback.pendingNext.value?.playbackId==imported[0].playbackId }
            playback.playNext(imported[1])
            waitFor("pending replacement") { playback.pendingNext.value?.playbackId==imported[1].playbackId }
            assertEquals(physicalQueue,withContext(Dispatchers.Main) { (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId } })
            withContext(Dispatchers.Main) { controller.seekToNext() }
            waitFor("explicit random next while paused") { controller.currentMediaItem?.mediaId==imported[1].playbackId && controller.playbackState==Player.STATE_READY }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            withContext(Dispatchers.Main) { controller.play() }
            waitFor("pending consumed on actual playback") { controller.isPlaying && playback.pendingNext.value==null }
            var repeated=false
            withContext(Dispatchers.Main) {
                controller.addListener(object: Player.Listener {
                    override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?,reason: Int) {
                        if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) repeated=true
                    }
                })
                controller.shuffleModeEnabled=false
                controller.repeatMode=Player.REPEAT_MODE_ONE
                controller.seekTo(controller.duration-150)
            }
            waitFor("natural single repeat keeps session identity") { repeated && controller.isPlaying && controller.currentPosition<3000 }
            withContext(Dispatchers.Main) { controller.pause() }

            // Real app-entry boundaries: no forced pause or restart on queue replacement.
            playback.setPlaybackMode(com.lalilu.lplayer.playback.PlaybackMode.LOOP)
            playback.updatePlaylist(imported, 0, true)
            waitFor("atomic app selection starts") { controller.isPlaying && controller.currentMediaItem?.mediaId == imported[0].playbackId && controller.currentPosition > 200 }
            // A queued item may temporarily be absent during source/library refresh. It must not veto another selection.
            val missingRow = database.audioDao().getAudiosByPlaybackIds(listOf(imported[1].playbackId)).first().single()
            database.audioDao().delete(missingRow)
            try {
            com.lalilu.lplayer.action.PlayerAction.PlayByKey(com.lalilu.lmedia.domain.model.MediaKey(imported[2].mediaSourceName, imported[2].id)).action()
            waitFor("playlist click selects another song") { controller.isPlaying && controller.currentMediaItem?.mediaId == imported[2].playbackId && controller.currentPosition > 200 }
            } finally { database.mediaDao().upsertAudio(imported[1]) }
            delay(500)
            assertEquals(imported[2].playbackId, playback.queue.currentItem()?.playbackId)
            playback.playAudio(imported[0])
            waitFor("playlist click can select back") { controller.isPlaying && controller.currentMediaItem?.mediaId == imported[0].playbackId && controller.currentPosition > 200 }
            val beforeReplacement = withContext(Dispatchers.Main) { controller.currentPosition }
            playback.updatePlaylist(imported.reversed(), -1, false)
            waitFor("replacement preserves selected song and play intent") { controller.isPlaying && controller.currentMediaItem?.mediaId == imported[0].playbackId && controller.currentPosition >= beforeReplacement }
            playback.pause()
            waitFor("pause before same selection") { !controller.playWhenReady }
            playback.seekTo(3500)
            waitFor("paused seek before same selection") { controller.currentPosition >= 3400 }
            playback.playAudio(imported[0])
            delay(200)
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            assertTrue(withContext(Dispatchers.Main) { controller.currentPosition >= 3400 })
            playback.editQueueWithReceipt({ remove(imported[0]) }) { receipt ->
                assertEquals(imported[2].playbackId, receipt.currentItem()?.playbackId)
            }
            waitFor("deleting last current wraps to first") { controller.currentMediaItem?.mediaId == imported[2].playbackId && controller.playbackState == Player.STATE_READY }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            playback.updatePlaylist(listOf(imported[1], imported[0]), -1, false)
            waitFor("replacement without current picks first") { controller.currentMediaItem?.mediaId == imported[1].playbackId && controller.playbackState == Player.STATE_READY }
            playback.editQueue { replaceAll(listOf(imported[0], imported[2])) }
            waitFor("queue-edit replacement selects first") { controller.currentMediaItem?.mediaId == imported[0].playbackId && controller.playbackState == Player.STATE_READY }
            playback.play()
            waitFor("background fixture playing") { controller.isPlaying }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            val backgroundPosition = withContext(Dispatchers.Main) { controller.currentPosition }
            delay(1200)
            assertTrue(withContext(Dispatchers.Main) { controller.isPlaying && controller.currentPosition > backgroundPosition + 500 })
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            try {
                val beforeScreenOff = withContext(Dispatchers.Main) { controller.currentPosition }
                delay(700)
                assertTrue(withContext(Dispatchers.Main) { controller.isPlaying && controller.currentPosition > beforeScreenOff })
                withContext(Dispatchers.Main) { controller.pause() }
                waitFor("system pause while screen off") { !controller.playWhenReady }
                val pausedPosition = withContext(Dispatchers.Main) { controller.currentPosition }
                delay(300)
                assertTrue(withContext(Dispatchers.Main) { kotlin.math.abs(controller.currentPosition - pausedPosition) < 150 })
            } finally { instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close() }
        } finally {
            withContext(Dispatchers.Main) {
                com.lalilu.lplayer.LPlayerKV.originalOutputRequested.value = originalOutputRequest
                browser?.apply {
                    pause()
                    setMediaItems(original,oldIndex.coerceAtLeast(0),oldPosition.coerceAtLeast(0))
                    shuffleModeEnabled=oldShuffle; repeatMode=oldRepeat
                }
            }
            // Wait for the application's mirrored queue before removing only this test's owned audio files.
            if(browser!=null) runCatching { waitFor("step 8") { LPlayer.instance.queue.stateSnapshot().list.none { current -> imported.any { it.playbackId==current.playbackId } } } }
            withContext(Dispatchers.Main) { browser?.release(); activity?.finish() }
            imported.forEach { audio -> source.delete(audio) { snapshot -> database.mediaDao().insert(snapshot,source.name) } }
            directory.deleteRecursively()
            bindings.setSourceEnabled(source.name,wasEnabled)
        }
    }
}
