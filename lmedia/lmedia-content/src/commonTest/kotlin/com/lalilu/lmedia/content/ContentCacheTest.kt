package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class MemoryStorage : CacheStorage {
    val files = mutableMapOf<String, ByteArray>()
    var failWrite = false
    override fun read(name: String) = files[name]?.copyOf()
    override fun writeAtomic(name: String, bytes: ByteArray) { if(failWrite) throw kotlinx.io.IOException("disk full"); files[name] = bytes.copyOf() }
    override fun delete(name: String) { files.remove(name) }
    override fun names() = files.keys.toList()
}
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContentCacheTest {
    private val d = ContentDescriptor(MediaKey("source", "song"), "account-root", "version-1", 200000)
    private fun bytes(offset: Long, count: Int) = ByteArray(count) { ((offset + it) % 251).toByte() }

    @Test fun cacheOnlyReadsDoNotFetchHolesOrTrustCorruptFragments() = runTest {
        val storage = MemoryStorage()
        var fetches = 0
        val cache = RangeContentCache(storage, RangeReader { _, offset, n -> fetches++; bytes(offset, n) }, io = StandardTestDispatcher(testScheduler))
        val handle = cache.open(d)
        handle.read(0, 100)
        assertContentEquals(bytes(0, 100), handle.readCached(0, 100))
        assertNull(handle.readCached(90, 20))
        assertEquals(1, fetches)
        val file = storage.files.keys.single { it.endsWith(".bin") }
        storage.files[file] = ByteArray(100)
        assertNull(handle.readCached(0, 100))
        assertEquals(1, fetches)
        assertContentEquals(bytes(0, 100), handle.read(0, 100))
        assertEquals(2, fetches)
        handle.close()
        assertFailsWith<IllegalStateException> { handle.readCached(0, 1) }
    }

    @Test fun inspectionAndRecencyWritesDoNotEmitNewCoverageEvents() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        var changes = 0
        val cache = RangeContentCache(MemoryStorage(), RangeReader { _, start, count -> bytes(start, count) }, io = io,
            onCoverageChanged = { _, _ -> changes++ })
        cache.open(d).apply { read(0, 100); close() }
        assertEquals(1, changes)
        cache.open(d).apply { cachedSpans(); read(0, 100); close() }
        assertEquals(1, changes, "Metadata readers must not trigger their own coverage callback again")
    }

    @Test fun quotaChangesKeepActiveContentAndWithdrawEvictedAvailability() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val coverage=mutableMapOf<ContentDescriptor,Boolean>()
        val cache=RangeContentCache(storage,RangeReader { _,s,n -> bytes(s,n) },io=io,
            onCoverageChanged={ descriptor,complete -> coverage[descriptor]=complete })
        val descriptor=d.copy(size=100)
        val handle=cache.open(descriptor)
        handle.read(0,50)
        assertEquals(false,coverage[descriptor])
        handle.read(50,50)
        assertEquals(true,coverage[descriptor])
        cache.updateQuota(0)
        assertEquals(true,coverage[descriptor])
        assertContentEquals(bytes(0,100),handle.read(0,100))
        handle.close()
        assertEquals(false,coverage[descriptor])
        assertTrue(storage.names().none { it.endsWith(".bin") })
    }

    @Test fun disjointReadsDoNotDownloadTheGapAndColdStartReadsOffline() = runTest {
        val storage = MemoryStorage(); val calls = mutableListOf<Long>()
        val io = StandardTestDispatcher(testScheduler)
        val cache = RangeContentCache(storage, RangeReader { _, start, count -> calls += start; bytes(start,count) }, io = io)
        val h = cache.open(d)
        h.read(0, 100); h.read(100000, 100)
        assertEquals(listOf(0L,100000L), calls)
        assertEquals(listOf(ByteSpan(0,100),ByteSpan(100000,100100)), h.cachedSpans())
        assertEquals(100100, h.cachedEndFrom(100020))
        assertEquals(50000, h.cachedEndFrom(50000))
        h.close()
        val offline = RangeContentCache(storage, RangeReader { _,_,_ -> error("offline") }, io = io).open(d)
        assertContentEquals(bytes(100020,30),offline.read(100020,30))
        assertFailsWith<IllegalStateException> { offline.read(100090,20) }
        offline.close()
    }
    @Test fun completeCacheRestartsWithoutAnyOriginLookup() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val descriptor=d.copy(size=100)
        val first=RangeContentCache(storage,RangeReader { _,s,n -> bytes(s,n) },io=io).open(descriptor)
        first.read(0,100); first.close()
        val next=RangeContentCache(storage,RangeReader { _,_,_ -> error("Must not resolve source") },io=io).open(descriptor)
        assertContentEquals(bytes(0,100),next.read(0,100))
        assertTrue(next.read(100,1).isEmpty())
        next.close()
    }
    @Test fun versionAndAccountChangesCannotReuseOldBytes() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val cache=RangeContentCache(storage,RangeReader { _,s,n -> bytes(s,n) },io=io)
        cache.open(d).apply { read(0,100); close() }
        val offline=RangeContentCache(storage,RangeReader { _,_,_ -> error("offline") },io=io)
        assertFailsWith<IllegalStateException> { offline.open(d.copy(revision="v2")).read(0,100) }
        assertFailsWith<IllegalStateException> { offline.open(d.copy(scope="other-account")).read(0,100) }
    }
    @Test fun corruptSegmentIsNotClaimedAsCachedAndIsRefetched() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler); var calls=0
        val cache=RangeContentCache(storage,RangeReader { _,s,n -> calls++; bytes(s,n) },io=io)
        val h=cache.open(d); h.read(0,100)
        storage.files[storage.names().first { it.endsWith(".bin") }] = ByteArray(100)
        assertEquals(0,h.cachedSpans().size)
        assertContentEquals(bytes(0,100),h.read(0,100)); assertEquals(2,calls)
        h.close()
    }
    @Test fun cancelOrDisableDuringFetchDoesNotPublishBytes() = runTest {
        val io=StandardTestDispatcher(testScheduler); val storage=MemoryStorage(); var enabled=true
        val gate=CompletableDeferred<Unit>()
        val cache=RangeContentCache(storage,RangeReader { _,s,n -> gate.await(); bytes(s,n) },{enabled},io=io)
        val h=cache.open(d)
        val operation=async { runCatching { h.read(1000,100) } }; runCurrent()
        enabled=false; gate.complete(Unit)
        assertTrue(operation.await().isFailure)
        assertTrue(storage.names().none { it.endsWith(".bin") })
        enabled=true
        val gate2=CompletableDeferred<Unit>()
        val h2=RangeContentCache(MemoryStorage(),RangeReader { _,s,n -> gate2.await(); bytes(s,n) },io=io).open(d)
        val cancelled=async { h2.read(0,100) }; runCurrent(); cancelled.cancel(); runCurrent()
        assertTrue(h2.cachedSpans().isEmpty()); h.close(); h2.close()
    }
    @Test fun concurrentIdenticalReadsFetchOnceButDifferentSongsAreIndependent() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler); val gate=CompletableDeferred<Unit>(); var calls=0
        val cache=RangeContentCache(storage,RangeReader { descriptor,s,n -> calls++; if(descriptor==d) gate.await(); bytes(s,n) },io=io)
        val first=cache.open(d); val second=cache.open(d)
        val a=async { first.read(0,100) }; val b=async { second.read(0,100) }; runCurrent()
        val other=cache.open(d.copy(key=MediaKey("source","other")))
        assertContentEquals(bytes(0,100),other.read(0,100))
        gate.complete(Unit); assertContentEquals(a.await(),b.await()); assertEquals(2,calls)
        first.close(); second.close(); other.close()
    }
    @Test fun quotaProtectsActiveHandleThenEvictsWhenClosed() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val cache=RangeContentCache(storage,RangeReader { _,s,n -> bytes(s,n) },quotaBytes=50,io=io)
        val h=cache.open(d); h.read(0,100)
        assertEquals(100,h.cachedEndFrom(0)); h.close()
        assertTrue(storage.names().none { it.endsWith(".bin") })
    }
    @Test fun shortResponseAndFailedAtomicWriteNeverCreateCoverage() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val h=RangeContentCache(storage,RangeReader { _,_,_ -> byteArrayOf(1) },io=io).open(d)
        assertFailsWith<IllegalArgumentException> { h.read(0,100) }; assertTrue(h.cachedSpans().isEmpty()); h.close()
        val good=RangeContentCache(storage,RangeReader { _,s,n -> bytes(s,n) },io=io).open(d)
        storage.failWrite=true
        assertContentEquals(bytes(0,100),good.read(0,100))
        storage.failWrite=false; assertTrue(good.cachedSpans().isEmpty()); good.close()
    }
    @Test fun partialDirectoryKeepsOldSongsCompleteRemovesAndStaleCommitsAreIgnored() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler); val catalog=CatalogCache(storage,io)
        val a=CatalogSong(d.key,"A",content=d); val b=CatalogSong(MediaKey("source","b"),"B")
        assertTrue(catalog.commit(catalog.beginRefresh(d.scope),listOf(a,b),ScanCoverage.Complete))
        val stale=catalog.beginRefresh(d.scope); val latest=catalog.beginRefresh(d.scope)
        assertTrue(catalog.commit(latest,listOf(a.copy(title="new")),ScanCoverage.Partial))
        assertFalse(catalog.commit(stale,emptyList(),ScanCoverage.Complete))
        assertEquals(listOf("new","B"),CatalogCache(storage,io).visible(d.scope).map { it.title })
        catalog.commit(catalog.beginRefresh(d.scope),listOf(a),ScanCoverage.Complete)
        assertEquals(listOf(a),catalog.visible(d.scope))
    }
    @Test fun disableRemovesVisibleCatalogAndRejectsInFlightRefreshAcrossRestart() = runTest {
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler); val catalog=CatalogCache(storage,io)
        val a=CatalogSong(d.key,"A",content=d)
        catalog.commit(catalog.beginRefresh(d.scope),listOf(a),ScanCoverage.Complete)
        val refresh=catalog.beginRefresh(d.scope); catalog.setEnabled(d.scope,false)
        assertFalse(catalog.commit(refresh,listOf(a),ScanCoverage.Complete))
        assertTrue(CatalogCache(storage,io).visible(d.scope).isEmpty())
        catalog.setEnabled(d.scope,true); assertEquals(listOf(a),catalog.visible(d.scope))
    }
    @Test fun failedIndexCommitRemovesUnreferencedFragmentsWithoutLosingExistingCache() = runTest {
        val memory = MemoryStorage()
        var failIndex = false
        val storage = object : CacheStorage by memory {
            override fun writeAtomic(name: String, bytes: ByteArray) {
                if (failIndex && name.endsWith(".index")) throw kotlinx.io.IOException("index disk full")
                memory.writeAtomic(name, bytes)
            }
        }
        val handle = RangeContentCache(storage, RangeReader { _, offset, n -> bytes(offset, n) },
            io = StandardTestDispatcher(testScheduler)).open(d)
        handle.read(0, 100)
        val before = memory.names().toSet()
        failIndex = true
        assertContentEquals(bytes(1000, 100000), handle.read(1000, 100000))
        assertEquals(before, memory.names().toSet(), "A rejected index must not leave unaccounted data files")
        assertContentEquals(bytes(0, 100), handle.readCached(0, 100))
        failIndex = false
        handle.close()
    }

}
