package com.lalilu.lmedia

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaSourceEnablementPersistenceTest {
    @BeforeTest
    fun setUp() {
        KVContext.kvMap.clear()
    }

    @AfterTest
    fun tearDown() {
        KVContext.kvMap.clear()
    }

    @Test
    fun newSourcesDefaultToEnabledAndDisabledStateIsRestored() {
        val store = mutableMapOf<String, Any?>()
        val first = PersistentMediaSourceEnablement(LMediaKV(InMemoryKVSaver(store)))

        assertTrue(first.isEnabled("SubsonicSource"))
        first.setEnabled("SubsonicSource", false)

        KVContext.kvMap.clear()
        val restored = PersistentMediaSourceEnablement(LMediaKV(InMemoryKVSaver(store)))
        assertFalse(restored.isEnabled("SubsonicSource"))
        assertTrue(restored.isEnabled("MediaStore"))
    }
}
