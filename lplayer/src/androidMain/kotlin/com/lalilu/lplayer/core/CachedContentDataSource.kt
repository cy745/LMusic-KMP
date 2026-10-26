package com.lalilu.lplayer.core

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.lalilu.lmedia.content.ContentDescriptor
import com.lalilu.lmedia.content.ContentHandle
import com.lalilu.lmedia.content.RangeContentCache
import com.lalilu.lmedia.content.PlaybackCacheFiller
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Media3 invokes this on its loader thread, never on the UI thread. Cancellation interrupts runBlocking. */
class CachedContentDataSource(
    private val cache: RangeContentCache,
    private val filler: PlaybackCacheFiller? = null,
    private val describe: (Uri) -> ContentDescriptor,
) : BaseDataSource(true) {
    private var handle: ContentHandle? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var pending = ByteArray(0)
    private var pendingOffset = 0
    override fun open(dataSpec: DataSpec): Long = guarded {
        transferInitializing(dataSpec)
        val descriptor = describe(dataSpec.uri)
        require(dataSpec.position <= descriptor.size) { "Read position past EOF" }
        handle = runBlocking { cache.open(descriptor) }
        uri = dataSpec.uri
        position = dataSpec.position
        pending = ByteArray(0)
        pendingOffset = 0
        remaining = (descriptor.size - position).let { if(dataSpec.length == C.LENGTH_UNSET.toLong()) it else minOf(it,dataSpec.length) }
        transferStarted(dataSpec)
        remaining
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = guarded {
        if(length == 0) return@guarded 0
        if(remaining == 0L) return@guarded C.RESULT_END_OF_INPUT
        if (pendingOffset == pending.size) {
            // Extractors often ask for a few bytes at a time. Batch transport/disk reads without fetching the seek gap.
            pending = runBlocking {
                val current = checkNotNull(handle)
                val read: suspend () -> ByteArray = { current.read(position, minOf(remaining, RangeContentCache.MAX_READ.toLong()).toInt()) }
                filler?.foregroundRead(current.descriptor, position, read) ?: read()
            }
            pendingOffset = 0
        }
        if(pending.isEmpty()) throw IOException("Unexpected cached content EOF")
        val count = minOf(length, pending.size-pendingOffset)
        pending.copyInto(buffer,offset,pendingOffset,pendingOffset+count)
        pendingOffset += count
        position += count; remaining -= count
        bytesTransferred(count)
        count
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        val current = handle ?: return
        handle = null
        try { runBlocking { current.close() } }
        finally { uri = null; pending = ByteArray(0); pendingOffset = 0; transferEnded() }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() } catch(error: Exception) {
        throw if(error is IOException) error else IOException("Content access failed",error)
    }
}

class ContentDataSourceFactory(
    private val fallback: DataSource.Factory,
    private val cache: RangeContentCache,
    private val filler: PlaybackCacheFiller? = null,
    private val describe: (Uri) -> ContentDescriptor,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners=mutableListOf<androidx.media3.datasource.TransferListener>()
        private var active: DataSource? = null
        override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) { listeners += listener; active?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            val next = if(dataSpec.uri.scheme == "lmusic-content") CachedContentDataSource(cache,filler,describe) else fallback.createDataSource()
            active = next
            listeners.forEach(next::addTransferListener)
            return next.open(dataSpec)
        }
        override fun read(buffer: ByteArray,offset: Int,length: Int) = checkNotNull(active).read(buffer,offset,length)
        override fun getUri() = active?.uri
        override fun getResponseHeaders() = active?.responseHeaders ?: emptyMap()
        override fun close() { try { active?.close() } finally { active=null } }
    }
}
