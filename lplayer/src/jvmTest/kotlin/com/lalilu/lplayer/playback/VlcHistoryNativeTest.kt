package com.lalilu.lplayer.playback

import com.lalilu.lplayer.player.VLCPlayerLoader
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import uk.co.caprica.vlcj.player.base.State
import java.nio.file.Files
import java.net.ServerSocket
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer

/** Opt-in native check: runs without a window or audible output, and is explicitly skipped in CI. */
class VlcHistoryNativeTest {
    @Test fun negativeSavedPositionIsClampedAfterPreparation() = runBlocking {
        withNativePlayer { player, fixture ->
            prepareVlcMedia(player, MediaData.Url(fixture), -12_000)
            assertEquals(State.PAUSED, player.status().state())
            assertTrue(abs(player.status().time()) < 100)
        }
    }

    @Test fun byteArrayMediaIsActuallyPreparedAndSeekable() = runBlocking {
        withNativePlayer { player, fixture ->
            prepareVlcMedia(player, MediaData.Bytes(java.io.File(fixture).readBytes()), 12_000)
            assertEquals(State.PAUSED, player.status().state())
            assertTrue(player.status().length() > 12_000)
            assertTrue(abs(player.status().time() - 12_000) <= 500)
        }
    }

    @Test fun httpMediaIsPreparedAndResumesFromSavedPosition() = runBlocking {
        withNativePlayer { player, fixture ->
            val bytes = java.io.File(fixture).readBytes()
            val extension = java.io.File(fixture).extension.lowercase()
            val mediaPath = "/audio.$extension"
            val contentType = when (extension) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                else -> "application/octet-stream"
            }
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext(mediaPath) { exchange ->
                exchange.use {
                    val range = exchange.requestHeaders.getFirst("Range")
                        ?.let { Regex("bytes=(\\d+)-").find(it)?.groupValues?.get(1)?.toInt() }
                    val offset = range ?: 0
                    exchange.responseHeaders.set("Content-Type", contentType)
                    exchange.responseHeaders.set("Accept-Ranges", "bytes")
                    if (offset !in bytes.indices) {
                        exchange.responseHeaders.set("Content-Range", "bytes */${bytes.size}")
                        exchange.sendResponseHeaders(416, -1)
                    } else {
                        if (range != null) exchange.responseHeaders.set(
                            "Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}"
                        )
                        exchange.sendResponseHeaders(if (range == null) 200 else 206, (bytes.size - offset).toLong())
                        exchange.responseBody.write(bytes, offset, bytes.size - offset)
                    }
                }
            }
            server.start()
            try {
                prepareVlcMedia(player, MediaData.Url("http://127.0.0.1:${server.address.port}$mediaPath"), 12_000)
                assertEquals(State.PAUSED, player.status().state())
                assertTrue(abs(player.status().time() - 12_000) <= 500)
                player.controls().play()
                withTimeout(5_000) {
                    while (!player.status().isPlaying || player.status().time() <= 12_000) delay(25)
                }
                player.controls().setPause(true)
                withTimeout(5_000) {
                    while (player.status().state() != State.PAUSED) delay(25)
                }
            } finally {
                player.controls().stop()
                server.stop(0)
            }
        }
    }

    @Test fun cancellingNetworkPreparationStopsNativeLoadAndAllowsRetry() = runBlocking {
        withNativePlayer { player, fixture ->
            coroutineScope {
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                    val accepted = CompletableDeferred<Socket>()
                    val serving = launch(Dispatchers.IO) {
                        try {
                            server.accept().use { socket ->
                                accepted.complete(socket)
                                awaitCancellation() // Deliberately withhold the HTTP response.
                            }
                        } catch (error: java.io.IOException) {
                            if (!server.isClosed) throw error
                        }
                    }
                    val loading = launch {
                        prepareVlcMedia(player, MediaData.Url("http://127.0.0.1:${server.localPort}/held.mp3"))
                    }
                    try {
                        withTimeout(5_000) { accepted.await() }
                        loading.cancel()
                        // Release the controlled server so shutdown need not wait for
                        // a long network timeout in this test environment.
                        serving.cancelAndJoin()
                        server.close()
                        withTimeout(5_000) { loading.join() }
                        assertTrue(loading.isCancelled)
                        assertFalse(player.status().isPlaying)
                        preparePaused(player, fixture)
                    } finally {
                        server.close()
                        serving.cancelAndJoin()
                        loading.cancelAndJoin()
                    }
                }
            }
        }
    }

    @Test fun missingMediaFailsPreparationAndAllowsSuccessfulRetry() = runBlocking {
        withNativePlayer { player, fixture ->
            assertFalse(java.io.File(fixture + ".lmusic-missing").exists())
            assertFailsWith<IllegalStateException> {
                prepareVlcMedia(player, MediaData.Url(fixture + ".lmusic-missing"))
            }
            assertFalse(player.status().isPlaying)
            preparePaused(player, fixture)
            assertEquals(State.PAUSED, player.status().state())
        }
    }

    @Test fun corruptMediaIsNotReportedAsPrepared() = runBlocking {
        withNativePlayer { player, fixture ->
            val file = Files.createTempFile("lmusic-invalid-audio-", ".mp3").toFile()
            try {
                file.writeText("This is not an audio file")
                assertFailsWith<IllegalStateException> { prepareVlcMedia(player, MediaData.Url(file.path)) }
                assertFalse(player.status().isPlaying)
                preparePaused(player, fixture)
            } finally { file.delete() }
        }
    }

    @Test fun startPausedSeekAndResumeRetainPosition() = runBlocking {
        withNativePlayer { player, fixture ->
            preparePaused(player, fixture)
            assertFalse(player.status().isPlaying)
            player.controls().setTime(12_000)
            withTimeout(5_000) {
                while (abs(player.status().time() - 12_000) > 500) delay(25)
            }
            player.controls().play()
            withTimeout(5_000) {
                while (!player.status().isPlaying || player.status().time() < 12_000) delay(25)
            }
            player.controls().setPause(true)
            withTimeout(5_000) { while (player.status().isPlaying) delay(25) }
            val position = player.status().time()
            delay(200)
            assertTrue(abs(player.status().time() - position) < 100)
        }
    }

    @Test fun pauseCancelsLateResolutionBeforeItRestartsNativePlayback() = runBlocking {
        withNativePlayer { player, fixture ->
            preparePaused(player, fixture)
            coroutineScope {
                val commands = LatestPlaybackCommand()
                val resolving = CompletableDeferred<Unit>()
                val resolved = CompletableDeferred<String>()
                var applied = false
                val loading = launch {
                    commands.run {
                        resolving.complete(Unit)
                        val file = resolved.await()
                        applied = true
                        assertTrue(player.media().prepare(file))
                        player.controls().play()
                    }
                }
                resolving.await()
                commands.run { player.controls().setPause(true) }
                resolved.complete(fixture)
                loading.join()
                assertTrue(loading.isCancelled)
                assertFalse(applied)
                assertFalse(player.status().isPlaying)
                val position = player.status().time()
                delay(200)
                assertFalse(player.status().isPlaying)
                assertTrue(abs(player.status().time() - position) < 100)
                // Cancellation must not poison the next explicit command.
                commands.run { player.controls().play() }
                withTimeout(5_000) {
                    while (!player.status().isPlaying || player.status().time() <= position) delay(25)
                }
            }
        }
    }

    private suspend fun withNativePlayer(block: suspend (MediaPlayer, String) -> Unit) {
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        val fixture = System.getenv("LMUSIC_NATIVE_AUDIO_FIXTURE")
        assumeTrue("Native VLC resources and audio fixture not configured", resources != null && fixture != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().join()
        val factory = MediaPlayerFactory(
            null as uk.co.caprica.vlcj.factory.discovery.NativeDiscovery?,
            "--verbose=2", "--no-video", "--aout=dummy",
        )
        val player = factory.mediaPlayers().newMediaPlayer()
        try {
            withContext(Dispatchers.IO.limitedParallelism(1)) { block(player, fixture!!) }
        } finally {
            player.controls().stop()
            player.release()
            factory.release()
        }
    }

    private suspend fun preparePaused(player: MediaPlayer, fixture: String) {
        prepareVlcMedia(player, MediaData.Url(fixture), 12_000)
        assertEquals(State.PAUSED, player.status().state())
    }
}
