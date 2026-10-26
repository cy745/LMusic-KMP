package com.lalilu.lmusic.prototype

import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.content.*
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.lplayer.core.ContentDataSourceFactory
import com.lalilu.lplayer.core.Media3CoreRuntime
import com.lalilu.playback.core.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/** Real sockets + decoder. Faults stay inside a test-owned loopback server, never affect user sources. */
class NetworkRecoveryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Server(val data: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val status = AtomicInteger(206)
        val failures = AtomicInteger()
        val requests = AtomicInteger()
        val url = "http://127.0.0.1:${socket.localPort}/audio.wav"
        val thread = Thread {
            while (!socket.isClosed) try {
                socket.accept().use { connection ->
                    connection.soTimeout = 3000
                    val reader = connection.getInputStream().bufferedReader()
                    reader.readLine()
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                    }
                    requests.incrementAndGet()
                    val code = status.get()
                    if (code != 206) {
                        failures.incrementAndGet()
                        if (code == -1) return@use // Simulate an abrupt network disconnect.
                        connection.getOutputStream().write("HTTP/1.1 $code Test failure\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        return@use
                    }
                    val (start, end) = headers.getValue("range").removePrefix("bytes=").split('-').map(String::toInt)
                    val header = "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-$end/${data.size}\r\nContent-Length: ${end-start+1}\r\nETag: \"v1\"\r\nConnection: close\r\n\r\n"
                    connection.getOutputStream().apply { write(header.toByteArray()); write(data, start, end-start+1); flush() }
                }
            } catch (_: Exception) { if (socket.isClosed) break }
        }.apply { isDaemon = true; start() }
        override fun close() { socket.close(); thread.join(4000) }
    }
    private suspend fun waitFor(label: String, condition: () -> Boolean) {
        try { withTimeout(15000) { while (!withContext(Dispatchers.Main) { condition() }) delay(25) } }
        catch (error: TimeoutCancellationException) { throw AssertionError(label, error) }
    }
    private fun scenario(block: suspend (Server, PlaybackCore, Media3CoreRuntime, TrackKey) -> Unit) = runBlocking {
        val directory = File(context.cacheDir, "network-recovery-${System.nanoTime()}").apply { mkdirs() }
        val file = PrototypeAudio.write(directory, "network", durationMs = 180000)
        val server = Server(file.readBytes())
        val http = HttpClient(OkHttp) { install(HttpTimeout) { connectTimeoutMillis=1000; socketTimeoutMillis=1000; requestTimeoutMillis=2000 } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val key = TrackKey("network-test", "song")
        val descriptor = ContentDescriptor(MediaKey(key.source, key.id), "test", "v1", file.length(), etag="\"v1\"")
        var core: PlaybackCore? = null
        try {
            val cache = RangeContentCache(FileCacheStorage(File(directory, "cache").path), HttpRangeReader(http) { ContentOrigin(server.url) }, io=Dispatchers.IO)
            val factory = ContentDataSourceFactory(DefaultDataSource.Factory(context), cache) { descriptor }
            val runtime = withContext(Dispatchers.Main) {
                Media3CoreRuntime(context, factory, gain=0f).also {
                    core = PlaybackCore(scope, ContentResolver { PlayableResource("lmusic-content://test") }, it)
                }
            }
            block(server, core!!, runtime, key)
        } finally {
            withContext(Dispatchers.Main) { core?.close(); scope.cancel() }
            http.close(); server.close(); directory.deleteRecursively()
        }
    }
    private suspend fun PlaybackCore.command(command: PlaybackCommand) = withContext(Dispatchers.Main) { dispatch(command) }

    @Test fun temporaryServerFailureRecoversPreparationButKeepsUsersPause() = scenario { server, core, runtime, key ->
        server.status.set(503)
        core.command(PlaybackCommand.ReplaceQueue(listOf(key), startPlayback=true))
        waitFor("first failed request") { server.failures.get() > 0 }
        core.command(PlaybackCommand.Pause)
        server.status.set(206)
        waitFor("prepared after network recovery") { core.state.value.phase == Phase.ReadyPaused && runtime.player.playbackState == Player.STATE_READY }
        assertEquals(key, core.state.value.selected)
        assertEquals(PlayIntent.Pause, core.state.value.intent)
        assertFalse(withContext(Dispatchers.Main) { runtime.player.playWhenReady })
        core.command(PlaybackCommand.Play)
        waitFor("explicit play after recovery") { runtime.player.isPlaying }
    }

    @Test fun expiredCredentialsFailWithoutRepeatedRequestsAndExplicitPlayRetriesCurrent() = scenario { server, core, runtime, key ->
        server.status.set(401)
        core.command(PlaybackCommand.ReplaceQueue(listOf(key)))
        waitFor("auth error") { core.state.value.phase == Phase.Failed }
        assertTrue(core.state.value.error.orEmpty().contains("authentication"))
        assertEquals(1, server.requests.get())
        delay(1200)
        assertEquals("Do not retry permanent HTTP errors", 1, server.requests.get())
        assertEquals(key, core.state.value.selected)
        server.status.set(206)
        core.command(PlaybackCommand.Play)
        waitFor("explicit retry succeeds") { core.state.value.phase == Phase.Playing && runtime.player.isPlaying }
        assertEquals(key, core.state.value.selected)
    }

    @Test fun disconnectWhileSeekingRecoversAtRequestedPositionWithoutAutoResumeAfterPause() = scenario { server, core, runtime, key ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(key), startPlayback=true))
        waitFor("initial playback") { runtime.player.isPlaying }
        server.status.set(-1)
        core.command(PlaybackCommand.Seek(150000))
        waitFor("missing seek range encountered disconnect") { server.failures.get() > 0 }
        core.command(PlaybackCommand.Pause)
        server.status.set(206)
        waitFor("seek range prepared after reconnect") { runtime.player.playbackState == Player.STATE_READY && runtime.player.currentPosition >= 149900 }
        assertEquals(key, core.state.value.selected)
        assertEquals(PlayIntent.Pause, core.state.value.intent)
        assertFalse(withContext(Dispatchers.Main) { runtime.player.playWhenReady })
        core.command(PlaybackCommand.Play)
        waitFor("resumed requested position") { runtime.player.isPlaying && runtime.player.currentPosition >= 150000 }
    }
    @Test fun prolongedOutageAfterPreparationHasFiniteRetriesAndKeepsPausedSelection() = scenario { server, core, runtime, key ->
        core.command(PlaybackCommand.ReplaceQueue(listOf(key)))
        waitFor("initial prepared item") { runtime.player.playbackState == Player.STATE_READY }
        server.status.set(503)
        core.command(PlaybackCommand.Seek(150000))
        withTimeout(35000) {
            while (withContext(Dispatchers.Main) { core.state.value.phase != Phase.Failed }) delay(50)
        }
        assertEquals(7, server.failures.get()) // Initial failure plus six retries; no endless background loader.
        assertEquals(key, core.state.value.selected)
        assertEquals(PlayIntent.Pause, core.state.value.intent)
        delay(600)
        assertEquals(7, server.failures.get())
        assertFalse(withContext(Dispatchers.Main) { runtime.player.playWhenReady })
    }

}
