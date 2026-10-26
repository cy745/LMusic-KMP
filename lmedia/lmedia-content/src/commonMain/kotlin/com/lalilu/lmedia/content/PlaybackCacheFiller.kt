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
    private data class DeferredFill(val selection: Selection, val storageUnavailable: Boolean = false)
    private val waiting = MutableStateFlow<DeferredFill?>(null)
    private val recoveryVersion = MutableStateFlow(0L)
    private val gate = Mutex()
    private val readers = MutableStateFlow(0)
    private var chunk: Deferred<ByteArray>? = null
    private data class Hint(val descriptor: ContentDescriptor, val offset: Long, val serial: Long)
    private var hint: Hint? = null
    private val worker = scope.launch {
        selected.collectLatest { selection ->
            val descriptor = selection.descriptor ?: return@collectLatest
            supervisorScope {
                var failures = 0
                var attemptedRecoveryVersion = recoveryVersion.value
                suspend fun retryAfter(error: Exception) {
                    onFailure(error) // A failed background fill never changes playback intent.
                    val deferred = DeferredFill(selection)
                    waiting.value = deferred
                    try {
                        if (recoveryVersion.value != attemptedRecoveryVersion) return
                        if (error.contentReadFailure()?.retryable == false || error is ContentUnavailable) {
                            awaitCancellation() // Credentials/source refresh or explicit selection reopens it.
                        }
                        val backoffMs = minOf(30_000L, 1000L shl failures.coerceAtMost(5))
                        failures++
                        delay(backoffMs)
                    } finally { waiting.compareAndSet(deferred, null) }
                }
                var opened: ContentHandle? = null
                while (opened == null) {
                    try {
                        attemptedRecoveryVersion = recoveryVersion.value
                        opened = cache.open(descriptor)
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { retryAfter(error) }
                }
                val handle = opened
                try {
                    var cursor = 0L
                    var walked = descriptor.size // Audit existing bytes before downloading anything.
                    var seenHint = -1L
                    while (currentCoroutineContext().isActive) {
                        try {
                            attemptedRecoveryVersion = recoveryVersion.value
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
                                // Foreground playback can use downloaded bytes even if the disk rejects them.
                                // Background filling must stop instead of downloading the same song forever.
                                if (handle.readCached(cursor, bytes.size) == null) {
                                    val deferred = DeferredFill(selection, storageUnavailable = true)
                                    waiting.value = deferred
                                    try {
                                        onFailure(kotlinx.io.IOException("Cache storage unavailable; background fill paused"))
                                        awaitCancellation()
                                    } finally { waiting.compareAndSet(deferred, null) }
                                }
                                failures = 0
                                walked += bytes.size
                                cursor = (cursor + bytes.size) % descriptor.size
                            } catch (cancelled: CancellationException) {
                                currentCoroutineContext().ensureActive() // Foreground preemption is not worker cancellation.
                            } finally {
                                gate.withLock { if (chunk === work) chunk = null }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            retryAfter(error)
                        }
                    }
                } finally { withContext(NonCancellable) { handle.close() } }
            }
        }
    }

    fun select(descriptor: ContentDescriptor?) {
        if (selected.value.descriptor == descriptor) { retry(); return }
        selected.update { Selection(descriptor, it.revision + 1) }
    }

    /** Retry only deferred work, never cancel a healthy download or discard cached bytes. */
    fun retry(sourceName: String? = null) {
        if (sourceName != null && selected.value.descriptor?.key?.sourceName != sourceName) return
        val deferred = waiting.value
        if (sourceName != null && deferred?.storageUnavailable == true) return
        recoveryVersion.update { it + 1 }
        val selection = deferred?.selection ?: return
        selected.compareAndSet(selection, selection.copy(revision = selection.revision + 1))
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
        try {
            val result = read()
            if (selected.value.descriptor == descriptor) retry(descriptor.key.sourceName) // A real cache miss succeeded again.
            return result
        }
        finally { withContext(NonCancellable) { gate.withLock { readers.value-- } } }
    }

    fun close() { worker.cancel() }
}
