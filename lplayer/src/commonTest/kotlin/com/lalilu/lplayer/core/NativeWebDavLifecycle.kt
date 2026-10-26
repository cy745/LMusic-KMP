package com.lalilu.lplayer.core

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.cache.MediaCacheController
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmedia.net.NetworkType
import com.lalilu.lmedia.source.webdav.*
import com.lalilu.lplayer.playback.*
import com.lalilu.playback.core.Phase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.*

/** Shared host contract exercised by both real AVFoundation and VLC, against a read-only DAV fixture. */
internal suspend fun verifyNativeWebDavLifecycle(
    root: String, url: String, username: String, password: String,
    scope: CoroutineScope, client: () -> HttpClient,
    engineFactory: (NativeContentAccess) -> PlaybackEngine,
) {
    KVContext.registerSaver(InMemoryKVSaver())
    val json = Json { ignoreUnknownKeys = true }
    val legacy = MediaCacheController({ "$root/legacy" }, json)
    val source = WebDavSource(HttpWebDavClientFactory(), { "$root/legacy" }, legacy, json,
        LMediaKV(InMemoryKVSaver()), { flowOf(NetworkType.WIFI) })
    var engine: PlaybackEngine? = null
    var access: NativeContentAccess? = null
    var playback: CorePlayback? = null
    val rows = MutableStateFlow<List<LAudio>>(emptyList())
    val repository = object : AudioRepository {
        override fun getAudios() = rows
        override fun getAudios(ids: List<String>) = rows.map { songs -> songs.filter { it.id in ids } }
        override fun getAudio(id: String) = rows.map { songs -> songs.firstOrNull { it.id == id } }
        override suspend fun clearUnavailableAudio() {}
    }
    val history = PlaybackHistoryImpl(object : HistoryStorage {
        override fun savedPosition() = 0L
        override fun savePosition(position: Long) {}
    })
    suspend fun until(label: String, check: () -> Boolean) {
        try { withTimeout(30000) { while (!check()) delay(25) } }
        catch (failure: TimeoutCancellationException) { throw AssertionError("$label: ${playback?.core?.state?.value}", failure) }
    }
    fun createAccess(http: HttpClient) = NativeContentAccess(scope, PlatformMediaSource.provide(source), repository,
        http, "$root/catalog", "$root/content", { true }, flowOf(64L),
        restoreMetadata = { restored -> if (rows.value.isEmpty()) rows.value = restored })
    fun attach(content: NativeContentAccess): CorePlayback {
        val native = engineFactory(content).also { engine = it }
        val runtime = EngineAudioRuntime(scope, listOf(native), contentAccess = content)
        return CorePlayback(scope, runtime, PlatformMediaSource.provide(source), repository, history).also { playback = it }
    }
    try {
        source.connect(url, username, password, "/").getOrThrow()
        rows.value = withTimeout(30000) { source.snapshot.filterNotNull().first { it.audios.size >= 2 }.audios }
            .filter { it.extra?.get("path")?.endsWith(".wav") == true }.take(2)
        assertEquals(2, rows.value.size, "Fixture must contain two WAV files")
        val songs = rows.value
        access = createAccess(client())
        val content = access
        var host = attach(content)
        host.updatePlaylist(songs, 0, false)
        until("paused preparation") { host.core.state.value.phase == Phase.ReadyPaused }
        val data = content.resolve(songs[0])
        until("background full fill") { content.canPreloadAfter(data) }
        assertFalse(host.isPlaying.value)
        host.seekTo(5000)
        until("paused seek settles") { abs(host.currentPosition() - 5000) < 600 }
        host.play()
        until("real output advances") { host.isPlaying.value && host.currentPosition() > 5300 }
        host.pause()
        until("paused native output") { engine?.state?.value?.isPlaying == false }
        val paused = host.currentPosition()
        delay(250)
        assertTrue(abs(host.currentPosition() - paused) < 300)
        host.skipToNext()
        until("paused manual next") { host.queue.currentItem()?.id == songs[1].id && host.core.state.value.phase == Phase.ReadyPaused }
        assertFalse(host.isPlaying.value)
        val secondData = content.resolve(songs[1])
        until("second fills") { content.canPreloadAfter(secondData) }
        host.setPlaybackMode(PlaybackMode.SHUFFLE)
        host.playNext(songs[0]); host.skipToNext()
        until("explicit random next") { host.queue.currentItem()?.id == songs[0].id && host.core.state.value.phase == Phase.ReadyPaused }
        host.setPlaybackMode(PlaybackMode.LOOP)
        host.seekTo(7000)
        until("resume position") { abs(host.currentPosition() - 7000) < 600 }
        val saved = PlaybackHistory.HistorySnapshot(songs.map { it.id }, 0, host.currentPosition(), songs.map { it.mediaSourceName })
        host.core.close(); delay(300); engine?.release()
        // Recreate access with empty repository. No HTTP may occur: only catalog + file cache remain.
        rows.value = emptyList()
        var offlineRequests = 0
        access = createAccess(HttpClient(MockEngine { offlineRequests++; error("Offline request") }))
        host = attach(access)
        until("catalog restores library") { rows.value.size >= 2 }
        host.updatePlaylist(rows.value.sortedBy { songs.indexOfFirst { original -> original.id == it.id } }, 0, false)
        until("offline preparation") { host.core.state.value.phase == Phase.ReadyPaused }
        host.onQueueRestored(saved)
        until("offline saved position") { abs(host.currentPosition() - saved.position) < 600 }
        host.play()
        until("offline output advances") { host.currentPosition() > saved.position + 300 }
        host.pause()
        assertEquals(0, offlineRequests)
        println("NATIVE_WEBDAV_LIFECYCLE_OK songs=${songs.size} offlineRequests=$offlineRequests")
    } finally {
        playback?.core?.close(); delay(250); engine?.release(); access?.close()
        source.deactivate(); source.coroutineContext[Job]?.cancel(); legacy.stop()
    }
}
