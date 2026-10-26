package com.lalilu.lmusic.prototype

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaSession
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.cache.MediaCacheController
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.net.NetworkType
import com.lalilu.lmedia.source.webdav.HttpWebDavClient
import com.lalilu.lmedia.source.webdav.WebDavClient
import com.lalilu.lmedia.source.webdav.WebDavSource
import com.lalilu.lplayer.core.*
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in read-only LAN test. Credentials are supplied as instrumentation arguments, never committed. */
class WebDavLiveBufferDeviceTest {
    @Test fun realFilesKeepDiskBufferAcrossSeekReplayAndMediaSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("webdavUrl")
        assumeTrue("Provide a read-only WebDAV fixture", !url.isNullOrBlank())
        val context = instrumentation.targetContext
        val prefix = "live-buffer-${System.nanoTime()}"
        val directory = File(context.cacheDir, prefix)
        val legacy = MediaCacheController({ directory.path }, Json { ignoreUnknownKeys = true })
        val source = WebDavSource(
            clientFactory = { config ->
                val http = HttpWebDavClient(config)
                object : WebDavClient by http {
                    override suspend fun propfind(path: String, depth: Int) = http.propfind(path, depth)
                        .filter { !it.isDirectory }.let { entries ->
                            listOfNotNull(entries.filter { it.path.endsWith(".flac", true) }.minByOrNull { it.contentLength },
                                entries.filter { it.path.endsWith(".mp3", true) }.minByOrNull { it.contentLength })
                        }
                }
            },
            cacheRootProvider = { directory.path }, cacheController = legacy,
            json = Json { ignoreUnknownKeys = true }, kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
            networkObservation = { flowOf(NetworkType.WIFI) },
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var access: PlaybackContentAccess? = null
        var core: PlaybackCore? = null
        var session: MediaSession? = null
        var browser: MediaBrowser? = null
        try {
            source.connect(url!!, args.getString("webdavUser").orEmpty(), args.getString("webdavPassword").orEmpty(), "/").getOrThrow()
            val songs = withTimeout(30000) { source.snapshot.filterNotNull().first().audios }
            assertEquals(2, songs.size)
            songs.forEach { println("LIVE_SELECTED path=${it.extra?.get("path")} size=${it.extra?.get("file_size")} id=${it.id}") }
            val repository = object : AudioRepository {
                override fun getAudios() = source.snapshot.map { it?.audios.orEmpty() }
                override fun getAudios(ids: List<String>) = getAudios().map { list -> list.filter { it.id in ids } }
                override fun getAudio(id: String) = getAudios(listOf(id)).map { it.singleOrNull() }
                override suspend fun clearUnavailableAudio() = Unit
            }
            access = PlaybackContentAccess(context, PlatformMediaSource(listOf(source)), repository, storagePrefix = prefix)
            val content = access
            content.remember(songs)
            val items = songs.associate { TrackKey(it.mediaSourceName, it.id) to MediaItem.Builder().setMediaId(it.playbackId).build() }.toMutableMap()
            lateinit var runtime: Media3CoreRuntime
            withContext(Dispatchers.Main) {
                runtime = Media3CoreRuntime(context, content.dataSourceFactory, contentAccess = content, cachedBufferTimeline = CachedBufferTimeline(content::cachedRanges))
                core = PlaybackCore(scope, ContentResolver { content.resolve(com.lalilu.lmedia.domain.model.MediaKey(it.source, it.id)) }, runtime)
                session = MediaSession.Builder(context, CoreSessionPlayer(core!!, runtime, scope, items)).setId(prefix).build()
                core!!.state.map { it.sessionPresentation() }.distinctUntilChanged()
                    .onEach { session?.setSessionExtras(it.toBundle()) }.launchIn(scope)
                browser = MediaBrowser.Builder(context, session!!.token).buildAsync().await()
            }
            val active = core!!
            val controller = browser!!
            suspend fun waitFor(label: String, predicate: () -> Boolean) {
                try { withTimeout(25000) { while (!withContext(Dispatchers.Main) { predicate() }) delay(100) } }
                catch (error: TimeoutCancellationException) {
                    throw AssertionError(withContext(Dispatchers.Main) { "$label: core=${active.state.value}, native=${runtime.player.bufferedPosition}, session=${controller.actualBufferedPosition()} rawSession=${controller.bufferedPosition}" }, error)
                }
            }
            for (song in songs) {
                val key = TrackKey(song.mediaSourceName, song.id)
                withContext(Dispatchers.Main) { active.dispatch(PlaybackCommand.ReplaceQueue(listOf(key))) }
                waitFor("native ready") { active.state.value.phase == Phase.ReadyPaused && controller.duration > 0 }
                // Production background filling must complete this paused selection without a test download.
                waitFor("complete file core buffer") { active.state.value.bufferedPositionMs == active.state.value.durationMs }
                waitFor("complete file controller buffer while paused") { controller.actualBufferedPosition() >= controller.duration - 1 }
                withContext(Dispatchers.Main) { active.dispatch(PlaybackCommand.Seek(active.state.value.durationMs / 2)) }
                waitFor("seek keeps full buffer") { active.state.value.phase == Phase.ReadyPaused && controller.actualBufferedPosition() >= controller.duration - 1 }
                withContext(Dispatchers.Main) { active.dispatch(PlaybackCommand.ReplaceQueue(emptyList())) }
                waitFor("empty before replay") { active.state.value.phase == Phase.Empty }
                withContext(Dispatchers.Main) { active.dispatch(PlaybackCommand.ReplaceQueue(listOf(key))) }
                waitFor("replay retains disk coverage") { active.state.value.phase == Phase.ReadyPaused && controller.duration > 0 && controller.actualBufferedPosition() >= controller.duration - 1 }
                println("LIVE_CACHE_OK format=${song.extra?.get("path")?.substringAfterLast('.')} size=${song.extra?.get("file_size")} duration=${active.state.value.durationMs}")
            }
        } finally {
            withContext(Dispatchers.Main) { browser?.release(); session?.release(); core?.close(); scope.cancel() }
            access?.close(); source.deactivate(); source.cancel(); legacy.stop()
            directory.deleteRecursively()
            File(context.cacheDir, "$prefix-content").deleteRecursively()
            File(context.filesDir, "$prefix-catalog").deleteRecursively()
        }
    }
}
