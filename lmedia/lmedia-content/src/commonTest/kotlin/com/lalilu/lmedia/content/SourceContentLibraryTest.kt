package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.*
import com.lalilu.lmedia.domain.source.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SourceContentLibraryTest {
    private class Source(override val name: String = "remote") : MediaSource {
        val store=MediaSourceStateStore()
        override val state=store.state
        override val snapshot=store.snapshot
        override val contentState=store.contentState
        var scope="account-1"
        var version="v1"
        var requests=0
        override val dataSource=object : MediaDataSource {
            override fun contentCacheScope()=scope
            override fun contentCacheIdentity(song:LAudio)=MediaCacheIdentity(scope,version,100,etag="\"$version\"")
            override suspend fun getMedia(song:LAudio):MediaData { requests++; return MediaData.Url("https://example.test/secret?token=runtime") }
            override fun originHeaders(song:LAudio)=mapOf("Authorization" to "Bearer private")
        }
    }
    @Test fun equalAccountNamesFromDifferentSourcesHaveIndependentDirectories() = runTest {
        val first=Source("one"); val second=Source("two")
        val library=SourceContentLibrary(PlatformMediaSource(listOf(first,second)),CatalogCache(MemoryStorage(),StandardTestDispatcher(testScheduler)))
        library.rememberComplete("one",listOf(LAudio("same","First",mediaSourceName="one")))
        library.rememberComplete("two",listOf(LAudio("same","Second",mediaSourceName="two")))
        assertEquals(setOf("First","Second"),library.cachedSongs().map { it.title }.toSet())
        library.rememberComplete("one",emptyList())
        assertEquals("Second",library.cachedSongs().single().title)
    }
    @Test fun cachedCatalogDoesNotWaitForSourceAndDisableOrAccountChangeHidesIt() = runTest {
        val source=Source(); var enabled=true
        val platform=PlatformMediaSource(listOf(source),object : MediaSourceEnablement {
            override fun isEnabled(sourceName:String)=enabled
            override fun setEnabled(sourceName:String,value:Boolean) { enabled=value }
        })
        val storage=MemoryStorage(); val io=StandardTestDispatcher(testScheduler)
        val library=SourceContentLibrary(platform,CatalogCache(storage,io))
        val song=LAudio("a","Title",mediaSourceName="remote",extra=mapOf("albumName" to "Album","url" to "secret","Authorization" to "private"))
        library.rememberComplete("remote",listOf(song))
        source.store.content.unavailable("offline")
        val restored=SourceContentLibrary(platform,CatalogCache(storage,io))
        val cached=restored.cachedSongs().single()
        assertEquals("Album",cached.metadata["albumName"])
        assertFalse(storage.files.values.any { it.decodeToString().contains("private") || it.decodeToString().contains("secret") })
        assertEquals(0,source.requests)
        enabled=false; assertTrue(restored.cachedSongs().isEmpty()); assertFalse(restored.isEnabled(cached.content!!))
        enabled=true; assertEquals(1,restored.cachedSongs().size)
        source.scope="account-2"; assertTrue(restored.cachedSongs().isEmpty()); assertFalse(restored.isEnabled(cached.content!!))
    }
    @Test fun onlyCacheMissResolvesOnlineAndRevisionChangeRejectsStaleLocator() = runTest {
        val source=Source(); source.store.content.ready()
        val library=SourceContentLibrary(PlatformMediaSource(listOf(source)),CatalogCache(MemoryStorage(),StandardTestDispatcher(testScheduler)))
        val song=LAudio("a","Title",mediaSourceName="remote")
        val descriptor=library.descriptor(song)!!
        val origin=library.origin(descriptor) { song }
        assertEquals("Bearer private",origin.headers["Authorization"]); assertEquals(1,source.requests)
        source.version="v2"
        assertFailsWith<IllegalStateException> { library.origin(descriptor) { song } }
        assertEquals(1,source.requests)
    }
}
