package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lplayer.core.NativeContentAccess
import com.lalilu.lplayer.player.VLCPlayerLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.nio.file.Files
import java.io.File
import kotlin.test.*

class VlcCachedContentNativeTest {
    @Test fun nativeCachedReadsSeekAndReopenOffline() = runBlocking {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        val fixture = System.getenv("LMUSIC_NATIVE_AUDIO_FIXTURE")
        assumeTrue(resources != null && fixture != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().await()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video", "--aout=dummy")
        val player = factory.mediaPlayers().newMediaPlayer()
        val root = Files.createTempDirectory("vlc-cache-test").toFile()
        val bytes = File(fixture!!).readBytes()
        val song = LAudio("cached", mediaSourceName = "cache-test")
        val store = MediaSourceStateStore().apply { content.ready() }
        val source = object : MediaSource {
            override val name = song.mediaSourceName
            override val state = store.state
            override val snapshot = store.snapshot
            override val contentState = store.contentState
            override val dataSource = object : MediaDataSource {
                override fun contentCacheScope() = "test"
                override fun contentCacheIdentity(song: LAudio) = MediaCacheIdentity("test", "v1", bytes.size.toLong())
                override suspend fun getMedia(song: LAudio) = MediaData.Url("https://test.invalid/music.wav")
            }
        }
        val repository = object : AudioRepository {
            override fun getAudios() = flowOf(listOf(song))
            override fun getAudios(ids: List<String>) = getAudios()
            override fun getAudio(id: String) = flowOf(song)
            override suspend fun clearUnavailableAudio() {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        val requests = java.util.concurrent.atomic.AtomicInteger()
        var offline = false
        fun access() = NativeContentAccess(scope, PlatformMediaSource.provide(source), repository,
            HttpClient(MockEngine { request ->
                check(!offline) { "Offline reopen tried network" }
                requests.incrementAndGet()
                val range = request.headers["Range"]!!.removePrefix("bytes=").split('-').map(String::toInt)
                respond(bytes.copyOfRange(range[0], range[1] + 1), HttpStatusCode.PartialContent,
                    headersOf("Content-Range", "bytes ${range[0]}-${range[1]}/${bytes.size}"))
            }), "${root.path}/catalog", "${root.path}/content", { true }, flowOf(64L))
        var access = access()
        var engine = VlcEngine(scope, { player }, access)
        try {
            val data = access.resolve(song)
            access.selectCurrent(data)
            engine.load(data, song); engine.awaitPreparation()
            engine.seekTo(1000)
            withTimeout(10000) { while (!access.canPreloadAfter(data)) delay(20) }
            assertTrue(engine.bufferedPositionAt(1000) > 1000)
            engine.release(); access.close()
            val count = requests.get()
            offline = true; store.content.unavailable("offline")
            access = access(); engine = VlcEngine(scope, { player }, access)
            engine.load(access.resolve(song), song); engine.awaitPreparation()
            engine.seekTo(1500)
            assertEquals(count, requests.get())
            assertFalse(engine.state.value.isPlaying)
        } finally {
            engine.release(); access.close(); scope.cancel()
            player.release(); factory.release(); root.deleteRecursively()
        }
    }
}
