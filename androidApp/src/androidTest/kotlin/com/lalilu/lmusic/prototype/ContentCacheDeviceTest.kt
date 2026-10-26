package com.lalilu.lmusic.prototype

import androidx.media3.datasource.DataSource
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.lmedia.content.*
import com.lalilu.lmedia.domain.model.MediaKey
import com.lalilu.playback.core.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Real loopback HTTP, durable disk, and real Media3 decoding. No server or user library required. */
class ContentCacheDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun client()=HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis=10000 } }
    private fun descriptor(size:Int)=ContentDescriptor(MediaKey("device-http","song"),"device-account-root","wav-v1",size.toLong(),etag="\"wav-v1\"")
    private class RangeServer(private val data:ByteArray, private val ignoreRange:Boolean=false) : AutoCloseable {
        private val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val requests=Collections.synchronizedList(mutableListOf<LongRange>())
        val url="http://127.0.0.1:${socket.localPort}/audio.wav"
        private val thread=Thread {
            while(!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        connection.soTimeout=5000
                        val reader=connection.getInputStream().bufferedReader()
                        reader.readLine()
                        val headers=mutableMapOf<String,String>()
                        while(true) { val line=reader.readLine() ?: break; if(line.isEmpty()) break
                            headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()
                        }
                        val range=Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["range"].orEmpty()) ?: error("Missing Range")
                        val start=range.groupValues[1].toInt(); val end=range.groupValues[2].toInt()
                        requests += start.toLong()..end.toLong()
                        val body=if(ignoreRange) data else data.copyOfRange(start,end+1)
                        val status=if(ignoreRange) "200 OK" else "206 Partial Content"
                        val rangeHeader=if(ignoreRange) "" else "Content-Range: bytes $start-$end/${data.size}\r\n"
                        val response="HTTP/1.1 $status\r\n${rangeHeader}Content-Length: ${body.size}\r\nETag: \"wav-v1\"\r\nContent-Type: audio/wav\r\nConnection: close\r\n\r\n"
                        connection.getOutputStream().apply { write(response.toByteArray()); write(body); flush() }
                    }
                } catch(error:Exception) { if(socket.isClosed) break }
            }
        }.apply { isDaemon=true; start() }
        override fun close() { socket.close(); thread.join(2000) }
    }
    private suspend fun PlaybackCore.command(value:PlaybackCommand)=withContext(Dispatchers.Main) { dispatch(value) }
    private suspend fun PlaybackCore.await(predicate:(PlaybackSnapshot)->Boolean)=withTimeout(15000) { state.first(predicate) }

    @Test fun seekDownloadsOnlyRequestedRangesAndReopensThemOffline()=runBlocking {
        val root=File(context.cacheDir,"p1-range-${System.nanoTime()}"); root.mkdirs()
        val bytes=ByteArray(300000){(it%251).toByte()}; val d=descriptor(bytes.size)
        val server=RangeServer(bytes); val http=client()
        try {
            val cache=RangeContentCache(FileCacheStorage(root.path),HttpRangeReader(http){ContentOrigin(server.url)},io=Dispatchers.IO)
            val handle=cache.open(d)
            assertArrayEquals(bytes.copyOfRange(0,100),handle.read(0,100))
            assertArrayEquals(bytes.copyOfRange(200000,200200),handle.read(200000,200))
            assertEquals(listOf(0L..99L,200000L..200199L),server.requests.toList())
            assertEquals(200200L,handle.cachedEndFrom(200050))
            assertEquals(100000L,handle.cachedEndFrom(100000))
            handle.close(); server.close(); http.close()
            val offline=RangeContentCache(FileCacheStorage(root.path),RangeReader { _,_,_ -> error("Offline") },io=Dispatchers.IO).open(d)
            assertArrayEquals(bytes.copyOfRange(200010,200030),offline.read(200010,20)); offline.close()
        } finally { http.close(); server.close(); root.deleteRecursively() }
    }

    @Test fun completedAudioAndMetadataPlayAndSeekAfterServerIsGone()=runBlocking {
        val root=File(context.cacheDir,"p1-offline-${System.nanoTime()}"); root.mkdirs()
        val bytes=PrototypeAudio.write(root,"fixture").readBytes(); val d=descriptor(bytes.size)
        val server=RangeServer(bytes); val http=client(); val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var core:PlaybackCore?=null
        try {
            val disk=File(root,"cache")
            val cache=RangeContentCache(FileCacheStorage(disk.path),HttpRangeReader(http){ContentOrigin(server.url)},io=Dispatchers.IO)
            val h=cache.open(d)
            var position=0L
            while(position<d.size) { position+=h.read(position,minOf(65536L,d.size-position).toInt()).size }
            assertEquals(d.size,h.cachedEndFrom(0)); h.close()
            val catalog=CatalogCache(FileCacheStorage(File(root,"catalog").path),Dispatchers.IO)
            catalog.commit(catalog.beginRefresh(d.scope),listOf(CatalogSong(d.key,"Offline WAV",content=d)),ScanCoverage.Complete)
            server.close(); http.close()
            val restored=CatalogCache(FileCacheStorage(File(root,"catalog").path),Dispatchers.IO).visible(d.scope).single()
            assertEquals("Offline WAV",restored.title)
            val misses=AtomicInteger()
            val offline=RangeContentCache(FileCacheStorage(disk.path),RangeReader { _,_,_ -> misses.incrementAndGet(); error("Source is unavailable") },io=Dispatchers.IO)
            withContext(Dispatchers.Main) {
                val runtime=Media3PrototypeRuntime(context,DataSource.Factory { CachedContentDataSource(offline) { restored.content!! } })
                core=PlaybackCore(parent,ContentResolver { PlayableResource("lmusic-content://offline") },runtime)
            }
            val active=core!!
            active.command(PlaybackCommand.ReplaceQueue(listOf(TrackKey(d.key.sourceName,d.key.id))))
            active.await { it.phase==Phase.ReadyPaused }
            active.command(PlaybackCommand.Play); active.await { it.phase==Phase.Playing && it.positionMs>100 }
            active.command(PlaybackCommand.Pause); delay(150)
            active.command(PlaybackCommand.Seek(5000))
            active.await { it.phase==Phase.ReadyPaused && it.pendingSeekId==null && it.positionMs>=4900 }
            active.command(PlaybackCommand.Play); active.await { it.phase==Phase.Playing && it.positionMs>5100 }
            assertEquals(0,misses.get())
        } finally {
            withContext(Dispatchers.Main) { core?.close(); parent.cancel() }
            http.close(); server.close(); root.deleteRecursively()
        }
    }

    @Test fun realHttpIgnoringRangeCannotPoisonDisk()=runBlocking {
        val root=File(context.cacheDir,"p1-invalid-${System.nanoTime()}"); root.mkdirs()
        val server=RangeServer(ByteArray(1000),ignoreRange=true); val http=client()
        try {
            val cache=RangeContentCache(FileCacheStorage(root.path),HttpRangeReader(http){ContentOrigin(server.url)},io=Dispatchers.IO)
            val h=cache.open(descriptor(1000))
            assertTrue(runCatching { h.read(500,100) }.isFailure)
            assertTrue(h.cachedSpans().isEmpty()); h.close()
        } finally { http.close(); server.close(); root.deleteRecursively() }
    }

    @Test fun realMedia3SeekRequestsFarRangeWithoutFillingTheMiddle()=runBlocking {
        val root=File(context.cacheDir,"p1-seek-${System.nanoTime()}"); root.mkdirs()
        val bytes=PrototypeAudio.write(root,"long",durationMs=180000).readBytes(); val d=descriptor(bytes.size)
        val server=RangeServer(bytes); val http=client(); val parent=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var core:PlaybackCore?=null
        try {
            val cache=RangeContentCache(FileCacheStorage(File(root,"cache").path),HttpRangeReader(http){ContentOrigin(server.url)},io=Dispatchers.IO)
            withContext(Dispatchers.Main) {
                core=PlaybackCore(parent,ContentResolver { PlayableResource("lmusic-content://online") },
                    Media3PrototypeRuntime(context,DataSource.Factory { CachedContentDataSource(cache) { d } }))
            }
            val active=core!!
            active.command(PlaybackCommand.ReplaceQueue(listOf(TrackKey(d.key.sourceName,d.key.id))))
            active.await { it.phase==Phase.ReadyPaused }
            active.command(PlaybackCommand.Seek(140000))
            active.command(PlaybackCommand.Play)
            active.await { it.phase==Phase.Playing && it.positionMs>=140100 }
            val requests=synchronized(server.requests) { server.requests.toList() }
            assertTrue(requests.any { it.first>4_000_000 })
            assertTrue(requests.all { it.last-it.first+1<=RangeContentCache.MAX_READ })
            // There is no request covering the unplayed middle of the 180-second file.
            assertFalse(requests.any { 3_000_000L in it })
        } finally {
            withContext(Dispatchers.Main) { core?.close(); parent.cancel() }
            http.close(); server.close(); root.deleteRecursively()
        }
    }
}
