package com.lalilu.lplayer.player

import com.lalilu.lmedia.content.ContentHandle
import com.lalilu.lplayer.core.NativeContentAccess
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import kotlinx.coroutines.*
import uk.co.caprica.vlcj.binding.internal.*
import uk.co.caprica.vlcj.binding.support.types.size_t
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** VLC byte callbacks run on native loader threads, never on the playback command dispatcher. */
internal class CachedCallbackMedia(private val access: NativeContentAccess, private val uri: String) : CallbackMedia {
    private val job = SupervisorJob()
    private val lock = ReentrantLock()
    private var handle: ContentHandle? = null
    private var position = 0L
    private val size = checkNotNull(access.descriptor(uri)).size
    @Volatile var failure: Throwable? = null
        private set
    private fun <T> read(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.IO + job) { block() } }
    private val openCallback = libvlc_media_open_cb { _, _, sizePointer ->
        try { lock.withLock {
            if (!job.isActive) return@libvlc_media_open_cb -1
            if (handle == null) handle = read { access.open(uri) }
            position = 0
            (sizePointer as LongByReference).value = size
            0
        } } catch (error: Exception) { failure = error; -1 }
    }
    private val readCallback = libvlc_media_read_cb { _, buffer, requested ->
        try { lock.withLock {
            if (position >= size) return@libvlc_media_read_cb size_t(0)
            val count = minOf(requested.toLong(), 64 * 1024L, size - position).toInt()
            val bytes = read { checkNotNull(handle).read(position, count) }
            buffer.write(0, bytes, 0, bytes.size)
            position += bytes.size
            size_t(bytes.size.toLong())
        } } catch (error: Exception) { failure = error; size_t(-1) }
    }
    private val seekCallback = libvlc_media_seek_cb { _, offset -> lock.withLock {
        if (!job.isActive || offset !in 0..size) -1 else { position = offset; 0 }
    } }
    private val closeCallback = libvlc_media_close_cb { _ ->
        try { close() } catch (error: Exception) { failure = error }
    }
    /** Cancel before stopping VLC, so a blocked network callback cannot deadlock native stop. */
    fun cancel() { job.cancel() }
    fun close() {
        cancel()
        lock.withLock { handle?.let { runBlocking { it.close() } }; handle = null }
    }
    override fun getOpen() = openCallback
    override fun getRead() = readCallback
    override fun getSeek() = seekCallback
    override fun getClose() = closeCallback
    override fun getOpaque(): Pointer? = null
}
