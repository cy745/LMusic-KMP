package com.lalilu.lmedia.content

import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class FileCacheStorageTest {
    @Test fun durableAudioAndCatalogSurviveReconstructionAndOrphansAreRemoved() = runTest {
        val root=Files.createTempDirectory("lmusic-p1-test").toFile()
        try {
            val d=ContentDescriptor(MediaKey("source","song"),"scope","v1",128)
            val storage=FileCacheStorage(root.path)
            val cache=RangeContentCache(storage,RangeReader { _,s,n -> ByteArray(n){(s+it).toByte()} })
            cache.open(d).apply { read(0,128); close() }
            val catalog=CatalogCache(storage)
            catalog.commit(catalog.beginRefresh("scope"),listOf(CatalogSong(d.key,"Saved song",content=d)),ScanCoverage.Complete)
            storage.writeAtomic("orphan.bin",byteArrayOf(1))
            root.resolve("interrupted.tmp").writeBytes(byteArrayOf(2))
            val reopened=FileCacheStorage(root.path)
            val offline=RangeContentCache(reopened,RangeReader { _,_,_ -> error("offline") }).open(d)
            assertContentEquals(ByteArray(128){it.toByte()},offline.read(0,128))
            assertEquals("Saved song",CatalogCache(reopened).visible("scope").single().title)
            assertFalse(root.resolve("orphan.bin").exists()); assertFalse(root.resolve("interrupted.tmp").exists())
            assertFailsWith<IllegalArgumentException> { storage.read("../outside") }
            offline.close()
        } finally { root.deleteRecursively() }
    }
}
