package com.lalilu.lmedia.content

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.io.IOException

@Serializable
private data class Fragment(val start: Long, val length: Int, val hash: String, val file: String) {
    val end: Long get() = start + length
}
@Serializable
private data class ContentIndex(val descriptor: ContentDescriptor, val fragments: List<Fragment> = emptyList(), val used: Long = 0)

/** Shared range cache. A single application owner per root; reads for different songs never share a network lock. */
class RangeContentCache(
    private val storage: CacheStorage,
    private val reader: RangeReader,
    private val isEnabled: (ContentDescriptor) -> Boolean = { true },
    private val quotaBytes: Long = 512L * 1024 * 1024,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    private val onCacheFailure: (IOException) -> Unit = {},
) {
    companion object { const val MAX_READ = 64 * 1024 }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private data class Entry(var index: ContentIndex, val reads: Mutex = Mutex(), var pins: Int = 0)
    private val entries = mutableMapOf<String, Entry>()
    private var initialized = false
    private var serial = 0L

    init { require(quotaBytes >= 0) }
    private fun id(d: ContentDescriptor) = digest(json.encodeToString(d))
    private fun requireEnabled(d: ContentDescriptor) {
        if (!isEnabled(d)) throw ContentUnavailable("Source or source configuration is no longer enabled")
    }
    private fun load() {
        if (initialized) return
        val names = storage.names()
        names.filter { it.endsWith(".index") }.forEach { file ->
            val index = runCatching { json.decodeFromString<ContentIndex>(storage.read(file)!!.decodeToString()) }.getOrNull()
            if (index != null && id(index.descriptor) + ".index" == file && index.fragments.all {
                    it.start >= 0 && it.length in 1..MAX_READ && it.start <= index.descriptor.size - it.length &&
                        it.file == "${id(index.descriptor)}-${it.start}-${it.hash}.bin"
                }) {
                entries[file.removeSuffix(".index")] = Entry(index)
                serial = maxOf(serial, index.used)
            } else storage.delete(file)
        }
        val referenced = entries.values.flatMap { it.index.fragments.map(Fragment::file) }.toSet()
        names.filter { it.endsWith(".tmp") || (it.endsWith(".bin") && it !in referenced) }.forEach(storage::delete)
        initialized = true
        evict()
    }
    private fun save(key: String, entry: Entry, next: ContentIndex) {
        storage.writeAtomic("$key.index", json.encodeToString(next).encodeToByteArray())
        entry.index = next
    }
    private inline fun bestEffortWrite(block: () -> Unit) {
        try { block() } catch (failure: IOException) { onCacheFailure(failure) }
    }
    private fun evict() {
        var size = entries.values.sumOf { it.index.fragments.sumOf { fragment -> fragment.length.toLong() } }
        entries.entries.filter { it.value.pins == 0 }.sortedBy { it.value.index.used }.forEach { (key, entry) ->
            if (size > quotaBytes) {
                storage.delete("$key.index")
                entry.index.fragments.forEach { storage.delete(it.file) }
                size -= entry.index.fragments.sumOf { it.length.toLong() }
                entries.remove(key)
            }
        }
    }
    suspend fun open(descriptor: ContentDescriptor): ContentHandle = withContext(io) {
        requireEnabled(descriptor)
        val key = id(descriptor)
        val entry = mutex.withLock {
            load()
            entries.getOrPut(key) { Entry(ContentIndex(descriptor)) }.also { it.pins++ }
        }
        object : ContentHandle {
            override val descriptor = descriptor
            private var closed = false // only accessed under the entry read mutex
            override suspend fun read(offset: Long, length: Int): ByteArray = withContext(io) {
                require(offset >= 0 && length in 1..MAX_READ)
                entry.reads.withLock {
                    check(!closed) { "Content handle is closed" }
                    requireEnabled(descriptor)
                    if (offset >= descriptor.size) return@withLock ByteArray(0)
                    val count = minOf(length.toLong(), descriptor.size - offset).toInt()
                    val output = ByteArray(count)
                    var written = 0
                    while (written < count) {
                        val position = offset + written
                        val cached = mutex.withLock {
                            val fragment = entry.index.fragments.firstOrNull { position >= it.start && position < it.end }
                            if (fragment == null) null else {
                                val bytes = storage.read(fragment.file)
                                if (bytes == null || bytes.size != fragment.length || digest(bytes) != fragment.hash) {
                                    save(key, entry, entry.index.copy(fragments = entry.index.fragments - fragment))
                                    storage.delete(fragment.file)
                                    null
                                } else {
                                    val take = minOf(count - written, (fragment.end - position).toInt())
                                    bytes.copyOfRange((position - fragment.start).toInt(), (position - fragment.start).toInt() + take)
                                }
                            }
                        }
                        val bytes = cached ?: run {
                            val size = mutex.withLock {
                                val nextStart = entry.index.fragments.filter { it.start > position }.minOfOrNull { it.start } ?: descriptor.size
                                minOf((count - written).toLong(), nextStart - position).toInt()
                            }
                            // Network work is outside the storage mutex. A cancelled fetch publishes nothing.
                            val fetched = reader.read(descriptor, position, size)
                            require(fetched.size == size) { "Truncated or oversized range response" }
                            requireEnabled(descriptor)
                            mutex.withLock {
                                val hash = digest(fetched)
                                val file = "$key-$position-$hash.bin"
                                bestEffortWrite {
                                    storage.writeAtomic(file, fetched)
                                    save(key, entry, entry.index.copy(fragments = entry.index.fragments + Fragment(position, size, hash, file), used = ++serial))
                                    evict()
                                }
                            }
                            fetched
                        }
                        bytes.copyInto(output, written)
                        written += bytes.size
                    }
                    requireEnabled(descriptor)
                    // Cache hits need no disk write. Persist recency on close or the next fragment commit.
                    mutex.withLock { entry.index = entry.index.copy(used = ++serial) }
                    output
                }
            }
            override suspend fun cachedSpans(): List<ByteSpan> = withContext(io) {
                entry.reads.withLock {
                    check(!closed)
                    requireEnabled(descriptor)
                    mutex.withLock {
                        // Coverage is a fact about durable bytes, not an optimistic download counter.
                        val valid = entry.index.fragments.filter { f ->
                            storage.read(f.file)?.let { it.size == f.length && digest(it) == f.hash } == true
                        }
                        if (valid.size != entry.index.fragments.size) save(key, entry, entry.index.copy(fragments = valid))
                        val result = mutableListOf<ByteSpan>()
                        valid.sortedBy { it.start }.forEach { f ->
                            val last = result.lastOrNull()
                            if (last != null && f.start <= last.endExclusive) result[result.lastIndex] = ByteSpan(last.start, maxOf(last.endExclusive, f.end))
                            else result += ByteSpan(f.start, f.end)
                        }
                        result
                    }
                }
            }
            override suspend fun cachedEndFrom(offset: Long): Long {
                require(offset >= 0)
                return cachedSpans().firstOrNull { offset >= it.start && offset < it.endExclusive }?.endExclusive ?: offset
            }
            override suspend fun close() = withContext(io) {
                entry.reads.withLock {
                    if (!closed) {
                        closed = true
                        mutex.withLock {
                            entry.pins--
                            bestEffortWrite { save(key, entry, entry.index); evict() }
                        }
                    }
                }
            }
        }
    }
}
