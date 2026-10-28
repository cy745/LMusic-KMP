package com.lalilu.extensions

import coil3.memory.MemoryCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedCoverTest {
    @Test fun handoffPreservesExactKeyExtrasAndElementIdentity() {
        val elements = mapOf("COVER" to "card:source/song", "TITLE" to "title:source/song")
        val key = MemoryCache.Key("source_song_7", mapOf("size" to "120x120", "transform:a" to "a:b/中文"))
        val carried = elements.withCoverKey(key)
        assertEquals(key, carried.coverKey())
        assertEquals(elements["COVER"], carried["COVER"])
        assertEquals(elements, carried.withCoverKey(null))
    }

    @Test fun replacingImageDropsStaleKeyExtras() {
        val old = emptyMap<String, String>().withCoverKey(MemoryCache.Key("old", mapOf("size" to "64")))
        assertEquals(MemoryCache.Key("new"), old.withCoverKey(MemoryCache.Key("new")).coverKey())
        assertNull(emptyMap<String, String>().coverKey())
    }
}
