package com.lalilu.lmedia.source

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileSystemSourceConfigPersistenceTest {
    private val store = mutableMapOf<String, Any?>()

    @BeforeTest
    fun setup() {
        KVContext.kvMap.clear()
        startKoin {
            modules(module { single { Json { ignoreUnknownKeys = true } } })
        }
    }

    @AfterTest
    fun tearDown() {
        KVContext.kvMap.clear()
        stopKoin()
    }

    @Test
    fun `configuration is persisted and restored as one value`() {
        val config = FileSystemSourceConfig(directoryBookmark = "/music/library")
        val first = LMediaKV(InMemoryKVSaver(store)).obtain(
            key = "FileSystemSourceConfigTest",
            defaultValue = FileSystemSourceConfig(),
        )

        first.value = config

        KVContext.kvMap.clear()
        val restored = LMediaKV(InMemoryKVSaver(store)).obtain(
            key = "FileSystemSourceConfigTest",
            defaultValue = FileSystemSourceConfig(),
        )

        assertEquals(config, restored.value)
    }
}
