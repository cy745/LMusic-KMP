package com.lalilu.lmusic.prototype

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.common.ext.md5
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.cache.MediaCacheController
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmedia.net.NetworkType
import com.lalilu.lmedia.source.webdav.*
import com.lalilu.lplayer.core.PlaybackContentAccess
import com.lalilu.lplayer.core.CachedBufferTimeline
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real range HTTP + production content access + native TagLib/Media3, with a fresh isolated source. */
class WebDavContentBridgeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private suspend fun until(label: String, condition: suspend () -> Boolean) {
        try { withTimeout(20000) { while (!condition()) delay(50) } }
        catch (error: TimeoutCancellationException) { throw AssertionError(label, error) }
    }
    private suspend fun fixture(bytes: ByteArray, filename: String, background: Boolean = false, songCount: Int = 1, onRequest: (String, LongRange) -> Unit = { _, _ -> }, test: suspend (WebDavSource, MediaCacheController, LAudio, PlaybackContentAccess, PlaybackCore, Media3CoreRuntime, String) -> Unit) {
        val directory = File(context.cacheDir, "webdav-bridge-${System.nanoTime()}")
        val prefix = "webdav-bridge-${System.nanoTime()}"
        val server = ContentCacheDeviceTest.RangeServer(bytes, onRequest = onRequest)
        val legacy = MediaCacheController({ directory.path }, Json { ignoreUnknownKeys = true })
        val source = WebDavSource(
            clientFactory = { object : WebDavClient {
                override suspend fun propfind(path: String, depth: Int) = (0 until songCount).map { WebDavEntry("/$it-$filename", false, bytes.size.toLong(), "\"wav-v1\"") }
                override suspend fun download(path: String): ByteArray? = null
                override suspend fun put(path: String, bytes: ByteArray, contentType: String) = error("No uploads")
                override suspend fun mkcol(path: String) = false
                override fun close() = Unit
            } },
            cacheRootProvider = { directory.path }, cacheController = legacy,
            json = Json { ignoreUnknownKeys = true }, kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
            networkObservation = { flowOf(NetworkType.WIFI) },
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var access: PlaybackContentAccess? = null
        var core: PlaybackCore? = null
        try {
            source.connect(server.url.substringBeforeLast('/'), "", "", "/").getOrThrow()
            val audio = withTimeout(10000) { source.snapshot.filterNotNull().first().audios.first() }
            val repository = object : AudioRepository {
                override fun getAudios() = source.snapshot.map { it?.audios.orEmpty() }
                override fun getAudios(ids: List<String>) = getAudios().map { rows -> rows.filter { it.id in ids } }
                override fun getAudio(id: String) = getAudios(listOf(id)).map { it.singleOrNull() }
                override suspend fun clearUnavailableAudio() = Unit
            }
            access = PlaybackContentAccess(context, PlatformMediaSource(listOf(source)), repository, storagePrefix = prefix)
            val activeAccess = access
            activeAccess.remember(source.snapshot.value!!.audios)
            val uri = activeAccess.resolve(audio.mediaKey).uri
            lateinit var runtime: Media3CoreRuntime
            withContext(Dispatchers.Main) {
                runtime = Media3CoreRuntime(context, activeAccess.dataSourceFactory, contentAccess = activeAccess.takeIf { background }, cachedBufferTimeline = CachedBufferTimeline(activeAccess::cachedRanges))
                core = PlaybackCore(scope, ContentResolver { activeAccess.resolve(com.lalilu.lmedia.domain.model.MediaKey(it.source, it.id)) }, runtime)
            }
            test(source, legacy, audio, activeAccess, core!!, runtime, uri)
        } finally {
            withContext(Dispatchers.Main) { core?.close(); scope.cancel() }
            access?.close()
            source.deactivate()
            source.cancel()
            server.close()
            directory.deleteRecursively()
            File(context.cacheDir, "$prefix-content").deleteRecursively()
            File(context.filesDir, "$prefix-catalog").deleteRecursively()
        }
    }

    @Test fun completingCacheWithIdenticalArtworkAndLyricsDoesNotInvalidatePresentation() = runBlocking {
        val bytes = instrumentation.context.assets.open("webdav/metadata.flac").use { it.readBytes() }
        fixture(bytes, "metadata.flac") { source, _, audio, _, _, _, _ ->
            val file = File.createTempFile("metadata-stages-", ".flac", context.cacheDir)
            try {
                file.writeBytes(bytes)
                val initial = source.contentState.value.forItem(audio.id).generation
                // Reuse identical tags at two extraction stages, so completion changes duration/status only.
                source.inspectCachedMedia(audio, bytes.size.toLong() / 2, bytes.size.toLong()) { it(file.path) }
                val partial = source.contentState.value.forItem(audio.id).generation
                assertTrue(partial > initial)
                val lyric = source.getLyric(audio)
                val cover = (source.getPicture(audio, MediaFetchOptions.EMPTY) as MediaData.Bytes).bytes
                source.inspectCachedMedia(audio, bytes.size.toLong(), bytes.size.toLong()) { it(file.path) }
                assertEquals(partial, source.contentState.value.forItem(audio.id).generation)
                assertEquals(lyric, source.getLyric(audio))
                assertArrayEquals(cover, (source.getPicture(audio, MediaFetchOptions.EMPTY) as MediaData.Bytes).bytes)
            } finally { file.delete() }
        }
    }

    @Test fun newCacheExtractsArtworkLyricsAndPublishesLibraryMetadataWithoutLegacyDownload() = runBlocking {
        val bytes = instrumentation.context.assets.open("webdav/metadata.flac").use { it.readBytes() }
        fixture(bytes, "metadata.flac") { source, legacy, audio, access, core, _, uri ->
            val generation = source.contentState.value.forItem(audio.id).generation
            assertNull(source.getLyric(audio))
            assertNull(source.getPicture(audio, MediaFetchOptions.EMPTY))
            withContext(Dispatchers.Main) { core.dispatch(PlaybackCommand.ReplaceQueue(listOf(TrackKey(audio.mediaSourceName, audio.id)))) }
            until("native preparation") { core.state.value.phase == Phase.ReadyPaused }
            until("embedded metadata reaches library") { source.snapshot.value?.audios?.single()?.title == "Cache metadata fixture" }
            val updated = source.snapshot.value!!.audios.single()
            assertTrue(source.getLyric(updated).orEmpty().contains("Cached lyric"))
            val picture = source.getPicture(updated, MediaFetchOptions.EMPTY) as? MediaData.Bytes
            assertNotNull(picture)
            assertTrue(picture!!.bytes.size > 20)
            assertTrue(source.contentState.value.forItem(audio.id).generation > generation)
            assertEquals(0L, legacy.filledSize(audio.id.md5()))
            until("complete disk cache is full duration even while paused") { core.state.value.durationMs > 0 && core.state.value.bufferedPositionMs == core.state.value.durationMs }
            assertEquals(bytes.size.toLong(), access.cachedRanges(uri)!!.second.single().endExclusive)
        }
    }

    @Test fun fillsCurrentBeforePreloadingOnlyNextPrefixAndPromotesNextOnSelection() = verifyFillPolicy(natural = false)
    @Test fun naturalTransitionPromotesCandidateToFullSongFilling() = verifyFillPolicy(natural = true)

    private fun verifyFillPolicy(natural: Boolean) = runBlocking {
        val directory = File(context.cacheDir, "fill-priority-${System.nanoTime()}")
        val bytes = PrototypeAudio.write(directory, "long", durationMs = 180000).readBytes()
        directory.deleteRecursively()
        val currentComplete = java.util.concurrent.atomic.AtomicBoolean(false)
        val prematureNext = java.util.concurrent.atomic.AtomicBoolean(false)
        val canPreload = java.util.concurrent.atomic.AtomicReference<() -> Boolean>({ false })
        fixture(bytes, "long.wav", background = true, songCount = 2, onRequest = { path, _ ->
            if (path.contains("1-long.wav") && !currentComplete.get()) {
                if (canPreload.get().invoke()) currentComplete.set(true) else prematureNext.set(true)
            }
        }) { source, _, audio, access, core, runtime, uri ->
            canPreload.set { access.canPreloadAfter(uri) }
            val songs = source.snapshot.value!!.audios
            val keys = songs.map { TrackKey(it.mediaSourceName, it.id) }
            withContext(Dispatchers.Main) { core.dispatch(PlaybackCommand.ReplaceQueue(keys)) }
            until("current fills autonomously") { access.canPreloadAfter(uri) }
            currentComplete.set(true)
            val nextUri = access.resolve(songs[1].mediaKey).uri
            until("next prefix preloaded") { access.cachedRanges(nextUri)?.second?.isNotEmpty() == true }
            delay(500)
            assertFalse("Next must not download before current is complete", prematureNext.get())
            val nextSpans = access.cachedRanges(nextUri)!!.second
            assertTrue("Next is only a prefix, not the whole song", nextSpans.sumOf { it.endExclusive - it.start } < bytes.size / 2)
            assertTrue("5-second WAV preload must not overshoot by a megabyte", nextSpans.sumOf { it.endExclusive - it.start } <= 512 * 1024)
            assertEquals(keys[0], core.state.value.selected)
            assertEquals(2, withContext(Dispatchers.Main) { runtime.player.mediaItemCount })
            println("CACHE_PRIORITY_OK currentBytes=${bytes.size} nextBytes=${nextSpans.sumOf { it.endExclusive - it.start }} natural=$natural")
            if (natural) {
                withContext(Dispatchers.Main) {
                    core.dispatch(PlaybackCommand.Seek(core.state.value.durationMs - 200))
                    core.dispatch(PlaybackCommand.Play)
                }
                until("natural promotion") { core.state.value.selected == keys[1] && core.state.value.phase == Phase.Playing }
                assertTrue(core.state.value.naturalTransition)
                withContext(Dispatchers.Main) { core.dispatch(PlaybackCommand.Pause) }
            } else withContext(Dispatchers.Main) { core.dispatch(PlaybackCommand.Select(keys[1])) }
            until("selected former candidate fills completely") { access.canPreloadAfter(nextUri) }
            assertEquals(keys[1], core.state.value.selected)
            assertEquals(PlayIntent.Pause, core.state.value.intent)
        }
    }

    @Test fun fullDiskCacheExtendsBeyondDecoderWindowAndSeekGapIsNotSummed() = runBlocking {
        val directory = File(context.cacheDir, "buffer-timeline-${System.nanoTime()}")
        val bytes = PrototypeAudio.write(directory, "long", durationMs = 180000).readBytes()
        directory.deleteRecursively()
        fixture(bytes, "long.wav") { _, _, audio, access, core, runtime, uri ->
            withContext(Dispatchers.Main) { core.dispatch(PlaybackCommand.ReplaceQueue(listOf(TrackKey(audio.mediaSourceName, audio.id)))) }
            until("initial buffer") { core.state.value.phase == Phase.ReadyPaused && core.state.value.bufferedPositionMs > 10000 }
            suspend fun readRange(position: Long, length: Long = C.LENGTH_UNSET.toLong()) = withContext(Dispatchers.IO) {
                val reader = access.dataSourceFactory.createDataSource()
                try {
                    reader.open(DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(position).setLength(length).build())
                    val buffer = ByteArray(65536)
                    while (reader.read(buffer, 0, buffer.size) != C.RESULT_END_OF_INPUT) Unit
                } finally { reader.close() }
            }
            readRange(4_480_044) // 140 seconds; the unplayed middle stays absent.
            until("separated tail recorded") { access.cachedRanges(uri)?.second?.size == 2 }
            assertTrue(core.state.value.bufferedPositionMs < 100000)
            readRange(0)
            until("disk timeline reaches actual end") { core.state.value.bufferedPositionMs >= 179900 }
            assertTrue(withContext(Dispatchers.Main) { runtime.player.bufferedPosition < 100000 })
        }
    }
}
