package com.lalilu.lmedia.content

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fills only the selected song. Decoder reads preempt a background network chunk, including after seek. */
class PlaybackCacheFiller(
    scope: CoroutineScope,
    private val cache: RangeContentCache,
    private val onFailure: (Exception) -> Unit = {},
) {
    private data class Selection(val descriptor: ContentDescriptor?, val revision: Long)
    private val selected = MutableStateFlow(Selection(null, 0))
    private val completed = MutableStateFlow<Selection?>(null)
    private val gate = Mutex()
    private val readers = MutableStateFlow(0)
    private var chunk: Deferred<ByteArray>? = null
    private data class Hint(val descriptor: ContentDescriptor, val offset: Long, val serial: Long)
    private var hint: Hint? = null
    private val worker = scope.launch {
        selected.collectLatest { selection ->
            val descriptor = selection.descriptor ?: return@collectLatest
            supervisorScope {
                var opened: ContentHandle? = null
                while (opened == null) {
                    try { opened = cache.open(descriptor) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { onFailure(error); delay(1000) }
                }
                val handle = opened
                try {
                    var cursor = 0L
                    var walked = descriptor.size // Audit existing bytes before downloading anything.
                    var seenHint = -1L
                    while (currentCoroutineContext().isActive) {
                        try {
                            if (walked >= descriptor.size) {
                                val spans = handle.cachedSpans()
                                if (spans.singleOrNull() == ByteSpan(0, descriptor.size)) {
                                    if (selected.value == selection) completed.value = selection
                                    awaitCancellation() // Keep current content pinned until selection changes.
                                }
                                walked = 0
                            }
                            readers.first { it == 0 }
                            val work = gate.withLock {
                                if (readers.value != 0) return@withLock null
                                hint?.takeIf { it.descriptor == descriptor && it.serial != seenHint }?.let {
                                    cursor = it.offset.coerceIn(0, descriptor.size - 1)
                                    walked = 0
                                    seenHint = it.serial
                                }
                                async(start = CoroutineStart.LAZY) {
                                    handle.read(cursor, minOf(RangeContentCache.MAX_READ.toLong(), descriptor.size - cursor).toInt())
                                }.also { chunk = it; it.start() }
                            } ?: continue
                            try {
                                val bytes = work.await()
                                check(bytes.isNotEmpty())
                                walked += bytes.size
                                cursor = (cursor + bytes.size) % descriptor.size
                            } catch (cancelled: CancellationException) {
                                currentCoroutineContext().ensureActive() // Foreground preemption is not worker cancellation.
                            } finally {
                                gate.withLock { if (chunk === work) chunk = null }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            onFailure(error) // Background failure must not interrupt a playable decoder window.
                            delay(1000)
                        }
                    }
                } finally { withContext(NonCancellable) { handle.close() } }
            }
        }
    }

    fun select(descriptor: ContentDescriptor?) {
        if (selected.value.descriptor == descriptor) return
        selected.update { Selection(descriptor, it.revision + 1) }
    }

    fun isComplete(descriptor: ContentDescriptor): Boolean {
        val current = selected.value
        return current.descriptor == descriptor && completed.value == current
    }

    suspend fun <T> foregroundRead(descriptor: ContentDescriptor, offset: Long, read: suspend () -> T): T {
        gate.withLock {
            readers.value++
            if (selected.value.descriptor == descriptor) hint = Hint(descriptor, offset, (hint?.serial ?: 0L) + 1)
            chunk?.cancel()
        }
        try { return read() }
        finally { withContext(NonCancellable) { gate.withLock { readers.value-- } } }
    }

    fun close() { worker.cancel() }
}
