package com.lalilu.lmusic.prototype

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.source.sandbox.SandboxMediaSource
import com.lalilu.lmusic.MainActivity
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.service.MService
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import org.junit.Assert.*
import org.junit.Test
import org.koin.mp.KoinPlatform
import java.io.File

/** Check both the legacy notification and platform MediaSession consumed by Android 13+ System UI. */
class SessionArtworkDeviceTest {
    @Test fun embeddedCoverReachesNotificationAndSystemSessionWithoutLeakingToNextTrack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val koin = KoinPlatform.getKoin()
        val source = koin.get<PlatformMediaSource>().sources.filterIsInstance<SandboxMediaSource>().single()
        val bindings = koin.get<MediaSourceBindingRepository>()
        bindings.startBinding()
        val wasEnabled = koin.get<PlatformMediaSource>().isEnabled(source)
        val database = koin.get<ILMediaDatabase>()
        val directory = File(context.cacheDir, "notification-test-${System.nanoTime()}").apply { mkdirs() }
        val imported = mutableListOf<LAudio>()
        var browser: MediaBrowser? = null
        var activity: android.app.Activity? = null
        var original = emptyList<MediaItem>()
        var oldIndex = 0
        var oldPosition = 0L
        var oldRepeat = androidx.media3.common.Player.REPEAT_MODE_OFF
        var oldShuffle = false
        suspend fun waitFor(label: String, condition: () -> Boolean) {
            try { withTimeout(20000) { while (!withContext(Dispatchers.Main) { condition() }) delay(50) } }
            catch (e: TimeoutCancellationException) { throw AssertionError(withContext(Dispatchers.Main) { "$label: id=${browser?.currentMediaItem?.mediaId}, state=${browser?.playbackState}, intent=${browser?.playWhenReady}, error=${browser?.playerError}" }, e) }
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL", "android.permission.POST_NOTIFICATIONS")
        try {
            bindings.setSourceEnabled(source.name, true)
            val covered = File(directory, "cover.flac").apply {
                writeBytes(instrumentation.context.assets.open("webdav/metadata.flac").use { it.readBytes() })
            }
            val plain = PrototypeAudio.write(directory, "plain", durationMs = 30000)
            for (file in listOf(covered, plain)) {
                val audio = source.import(PlatformFile(file), emptyList()).audio
                imported += audio
                database.mediaDao().upsertAudio(audio)
            }
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            browser = withContext(Dispatchers.Main) {
                MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, MService::class.java))).buildAsync().await()
            }
            val controller = browser!!
            withContext(Dispatchers.Main) {
                original = (0 until controller.mediaItemCount).map(controller::getMediaItemAt)
                oldIndex = controller.currentMediaItemIndex
                oldPosition = controller.currentPosition
                oldRepeat = controller.repeatMode
                oldShuffle = controller.shuffleModeEnabled
                controller.pause()
            }
            val notifications = context.getSystemService(NotificationManager::class.java)
            val sessions = context.getSystemService(MediaSessionManager::class.java)
            fun systemCover() = sessions.getActiveSessions(null).firstOrNull { it.packageName == context.packageName }
                ?.metadata?.let { it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) }
            fun notificationCover() = notifications.activeNotifications.firstOrNull {
                it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == imported[0].title
            }?.notification?.getLargeIcon()
            LPlayer.instance.setPlaybackMode(com.lalilu.lplayer.playback.PlaybackMode.SINGLE_LOOP)
            LPlayer.instance.updatePlaylist(imported, 0, false)
            waitFor("selected track ready") { controller.playbackState == androidx.media3.common.Player.STATE_READY && controller.currentMediaItem?.mediaId == imported[0].playbackId }
            waitFor("Media3 cover published") { controller.mediaMetadata.artworkData != null }
            val data = withContext(Dispatchers.Main) { controller.mediaMetadata.artworkData!! }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            assertNotNull(bitmap)
            assertTrue("bounded session payload", data.size < 300000)
            withContext(Dispatchers.Main) { controller.play() }
            waitFor("platform session cover") { systemCover() != null }
            waitFor("notification large icon") { notificationCover() != null }
            withContext(Dispatchers.Main) { controller.pause() }
            waitFor("paused with cover intact") { !controller.playWhenReady && notificationCover() != null && systemCover() != null }
            withContext(Dispatchers.Main) { controller.play(); controller.pause(); controller.play() }
            waitFor("cover survives repeated notification rebuilds") { controller.isPlaying && notificationCover() != null && systemCover() != null }
            withContext(Dispatchers.Main) { controller.seekToNextMediaItem() }
            waitFor("plain song selected without old artwork") {
                controller.currentMediaItem?.mediaId == imported[1].playbackId && controller.mediaMetadata.artworkData == null
            }
            waitFor("system cleared previous cover") { systemCover() == null }
            delay(500)
            assertNull(withContext(Dispatchers.Main) { controller.mediaMetadata.artworkData })
            withContext(Dispatchers.Main) { controller.seekToPreviousMediaItem() }
            waitFor("return to covered song") { controller.currentMediaItem?.mediaId == imported[0].playbackId && controller.mediaMetadata.artworkData != null && systemCover() != null }
            println("SESSION_ARTWORK media3=${data.size} bytes; platform bitmap and notification icon verified; pause/resume/select passed")
        } finally {
            withContext(Dispatchers.Main) {
                browser?.apply { pause(); setMediaItems(original, oldIndex.coerceAtLeast(0), oldPosition.coerceAtLeast(0)); repeatMode = oldRepeat; shuffleModeEnabled = oldShuffle }
            }
            if (browser != null) runCatching { waitFor("restore original queue") { LPlayer.instance.queue.stateSnapshot().list.none { item -> imported.any { it.playbackId == item.playbackId } } } }
            withContext(Dispatchers.Main) { browser?.release(); activity?.finish() }
            imported.forEach { source.delete(it) { snapshot -> database.mediaDao().insert(snapshot, source.name) } }
            directory.deleteRecursively()
            bindings.setSourceEnabled(source.name, wasEnabled)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
