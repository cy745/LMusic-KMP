package com.lalilu.lmusic.prototype

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.content.*
import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.CachedMediaAvailability
import com.lalilu.lmedia.source.webdav.WebDavConfig
import com.lalilu.lmedia.source.webdav.WebDavSource
import com.lalilu.lplayer.LPlayer
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.playback.HistoryStorageImpl
import com.lalilu.lplayer.playback.PlaybackMode
import com.lalilu.lplayer.service.MService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.mp.KoinPlatform
import java.io.File

/** Each phase is a separate instrumentation process. Run with scripts/test-android-cold-start.sh. */
class ColdStartDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val koin get() = KoinPlatform.getKoin()
    private val manifest get() = File(context.filesDir, "cold-start-test.json")
    private val sources get() = koin.get<PlatformMediaSource>()
    private val source get() = sources.sources.filterIsInstance<WebDavSource>().single()
    private val database get() = koin.get<ILMediaDatabase>()
    private val bindings get() = koin.get<MediaSourceBindingRepository>()
    private fun cacheFiles() = listOf(File(context.cacheDir, "plan-b-content"), File(context.filesDir, "plan-b-catalog"))
        .flatMap { root -> root.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() }.toSet()
    private fun read() = JSONObject(manifest.readText())
    private fun songs(state: JSONObject) = Json.decodeFromString<List<LAudio>>(state.getString("songs"))
    private suspend fun waitFor(label: String, timeoutMs: Long = 20000, predicate: () -> Boolean) {
        try { withTimeout(timeoutMs) { while (!withContext(Dispatchers.Main) { predicate() }) delay(50) } }
        catch (error: TimeoutCancellationException) { throw AssertionError("Timed out: $label", error) }
    }
    private suspend fun browser() = withContext(Dispatchers.Main) {
        MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, MService::class.java))).buildAsync().await()
    }
    private fun launch() = instrumentation.startActivitySync(Intent(context, com.lalilu.lmusic.MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    @Test fun coldStartPhase() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("coldStartPhase")
        assumeTrue("Requires separate processes; use the cold-start script", phase != null)
        when (phase) {
            "prepare" -> prepare()
            "removeLibraryRows" -> removeLibraryRows()
            "verify" -> verify()
            "removeCachedBytes" -> removeCachedBytes()
            "verifyMissingBytes" -> verifyMissingBytes()
            "cleanup" -> cleanup()
            else -> error("Unknown phase")
        }
    }

    private suspend fun prepare() {
        check(!manifest.exists()) { "Previous fixture requires cleanup first" }
        // This file stays inside the app sandbox; saved source credentials must never enter test logs.
        val state = JSONObject().put("history", LPlayerKV.historyPlaybackQueue.value)
            .put("reset", LPlayerKV.historyPositionResetRequested.value)
            .put("autoPlay", LPlayerKV.autoPlayWhenRestart.value).put("mode", LPlayerKV.playMode.value)
            .put("cacheEnabled", LPlayerKV.contentCacheEnabled.value)
            .put("sourceEnabled", sources.isEnabled(source))
            .put("config", Json.encodeToString(source.config.value))
            .put("cacheFiles", JSONArray()).put("songs", "[]")
        manifest.writeText(state.toString())
        LPlayerKV.autoPlayWhenRestart.value = false
        LPlayerKV.historyPlaybackQueue.value = ""
        LPlayerKV.historyPositionResetRequested.value = false
        LPlayerKV.contentCacheEnabled.value = true
        // No server exists, including during verification. Seed only the real generic disk cache.
        source.connect("http://127.0.0.1:1", "", "", "/cold-${System.nanoTime()}/").getOrThrow()
        assertTrue(bindings.setSourceEnabled(source.name, true))
        val previousFiles = cacheFiles()
        val directory = File(context.cacheDir, "cold-start-audio")
        val bytes = PrototypeAudio.write(directory, "fixture", durationMs = 20000).readBytes()
        directory.deleteRecursively()
        val items = (0..2).map { index -> LAudio(
            id = "cold-${System.nanoTime()}-$index", title = "Cold start $index", mediaSourceName = source.name,
            extra = mapOf("path" to "/cold-$index.wav", "file_size" to bytes.size.toString(), "etag" to "\"cold-$index\"", "duration" to "20000")) }
        state.put("songs", Json.encodeToString(items))
        manifest.writeText(state.toString())
        val catalog = CatalogCache(FileCacheStorage(File(context.filesDir, "plan-b-catalog").path), Dispatchers.IO)
        val library = SourceContentLibrary(sources, catalog)
        val cache = RangeContentCache(FileCacheStorage(File(context.cacheDir, "plan-b-content").path),
            RangeReader { _, offset, length -> bytes.copyOfRange(offset.toInt(), offset.toInt() + length) }, io = Dispatchers.IO)
        try {
            items.forEach { audio ->
                val descriptor = requireNotNull(library.descriptor(audio))
                val handle = cache.open(descriptor)
                try {
                    var offset = 0L
                    while (offset < descriptor.size) offset += handle.read(offset, minOf(65536L, descriptor.size - offset).toInt()).size
                    assertEquals(descriptor.size, handle.cachedEndFrom(0))
                } finally { handle.close() }
                database.mediaDao().upsertAudio(audio)
            }
            library.remember(items)
        } finally {
            state.put("cacheFiles", JSONArray((cacheFiles() - previousFiles).toList()))
            manifest.writeText(state.toString())
        }
        val activity = launch()
        val controller = browser()
        try {
            val playback = LPlayer.instance
            playback.setPlaybackMode(PlaybackMode.LOOP)
            playback.updatePlaylist(items, 1, false)
            waitFor("cached fixture ready") { controller.playbackState == Player.STATE_READY && controller.currentMediaItem?.mediaId == items[1].playbackId }
            playback.seekTo(3500)
            waitFor("paused queue and seek saved") {
                val storage = HistoryStorageImpl()
                val identity = storage.savedQueueIdentity()
                storage.savedPosition() in 3400L..3600L && identity?.index == 1 &&
                    identity.ids == items.map { it.id } && identity.sourceNames == items.map { it.mediaSourceName }
            }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            waitFor("cached songs exposed as available offline") { items.all { it.mediaKey in CachedMediaAvailability.complete.value } }
            state.put("preparedPid", android.os.Process.myPid())
            manifest.writeText(state.toString())
        } finally { withContext(Dispatchers.Main) { controller.release(); activity.finish() } }
    }

    private suspend fun removeLibraryRows() {
        // No player/activity is created in this process. Force restoration from the cached catalog.
        val items = songs(read())
        database.audioDao().getAudiosByPlaybackIds(items.map { it.playbackId }).first().forEach { database.audioDao().delete(it) }
        assertTrue(database.audioDao().getAudiosByPlaybackIds(items.map { it.playbackId }).first().isEmpty())
    }

    private suspend fun verify() {
        val state = read()
        assertNotEquals(state.getInt("preparedPid"), android.os.Process.myPid())
        val items = songs(state)
        val activity = launch()
        val controller = browser()
        try {
            waitFor("cold queue, saved position and paused preparation") {
                controller.mediaItemCount == 3 && controller.currentMediaItem?.mediaId == items[1].playbackId &&
                    controller.playbackState == Player.STATE_READY && controller.currentPosition in 3400L..3600L
            }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            assertEquals(items.map { it.playbackId }, withContext(Dispatchers.Main) { (0..2).map { controller.getMediaItemAt(it).mediaId } })
            waitFor("offline availability rebuilt after process death") { items.all { it.mediaKey in CachedMediaAvailability.complete.value } }
            val playback = LPlayer.instance
            playback.play()
            waitFor("offline playback advances") { controller.isPlaying && controller.currentPosition > 3800 }
            playback.pause()
            waitFor("pause takes effect") { !controller.playWhenReady }
            playback.seekTo(12000)
            waitFor("offline far seek") { controller.playbackState == Player.STATE_READY && controller.currentPosition in 11900L..12100L }
            // Queue control must remain responsive after cold restoration, including rapid replacements.
            repeat(11) { playback.skipToNext() }
            waitFor("rapid paused selection settles") { controller.currentMediaItem?.mediaId == items[0].playbackId && controller.playbackState == Player.STATE_READY && controller.currentPosition < 100 }
            assertFalse(withContext(Dispatchers.Main) { controller.playWhenReady })
            playback.play()
            waitFor("final selection plays") { controller.isPlaying }
            playback.seekTo(19500)
            waitFor("offline natural next") { controller.currentMediaItem?.mediaId == items[1].playbackId && controller.isPlaying }
            playback.pause()
            waitFor("pause after natural transition") { !controller.playWhenReady }
            playback.seekTo(3500)
            waitFor("second restart checkpoint saved") { HistoryStorageImpl().savedPosition() in 3400L..3600L && HistoryStorageImpl().savedQueueIdentity()?.index == 1 }
        } finally { withContext(Dispatchers.Main) { controller.release(); activity.finish() } }
    }

    private fun removeCachedBytes() {
        val selected = songs(read())[1]
        val root = File(context.cacheDir, "plan-b-content")
        val index = root.listFiles().orEmpty().filter { it.extension == "index" }.single {
            JSONObject(it.readText()).getJSONObject("descriptor").getJSONObject("key").getString("id") == selected.id
        }
        val fragments = JSONObject(index.readText()).getJSONArray("fragments")
        assertTrue(fragments.length() > 0)
        repeat(fragments.length()) { assertTrue(File(root, fragments.getJSONObject(it).getString("file")).delete()) }
        // Keep the index: startup must validate actual bytes rather than trusting its coverage claims.
    }

    private suspend fun verifyMissingBytes() {
        val items = songs(read())
        val activity = launch()
        val controller = browser()
        try {
            // Foreground network recovery now has a bounded ~24s retry window (30s initial preparation cap).
            waitFor("missing bytes fail paused current", timeoutMs = 35000) { controller.currentMediaItem?.mediaId == items[1].playbackId && controller.playerError != null && !controller.playWhenReady }
            assertFalse(items[1].mediaKey in CachedMediaAvailability.complete.value)
            delay(300)
            assertEquals(items[1].playbackId, withContext(Dispatchers.Main) { controller.currentMediaItem?.mediaId })
            assertFalse(withContext(Dispatchers.Main) { controller.isPlaying })
            LPlayer.instance.play()
            waitFor("explicit retry may skip failed current", timeoutMs = 35000) { controller.currentMediaItem?.mediaId == items[2].playbackId && controller.isPlaying }
            LPlayer.instance.pause()
        } finally { withContext(Dispatchers.Main) { controller.release(); activity.finish() } }
    }

    private suspend fun cleanup() {
        if (!manifest.exists()) return
        val state = read()
        removeLibraryRows()
        source.config.value = Json.decodeFromString<WebDavConfig>(state.getString("config"))
        source.config.save()
        bindings.setSourceEnabled(source.name, state.getBoolean("sourceEnabled"))
        LPlayerKV.historyPlaybackQueue.value = state.getString("history")
        LPlayerKV.historyPositionResetRequested.value = state.getBoolean("reset")
        LPlayerKV.autoPlayWhenRestart.value = state.getBoolean("autoPlay")
        LPlayerKV.playMode.value = state.getString("mode")
        LPlayerKV.contentCacheEnabled.value = state.getBoolean("cacheEnabled")
        val files = state.getJSONArray("cacheFiles")
        repeat(files.length()) { File(files.getString(it)).delete() }
        manifest.delete()
    }
}
