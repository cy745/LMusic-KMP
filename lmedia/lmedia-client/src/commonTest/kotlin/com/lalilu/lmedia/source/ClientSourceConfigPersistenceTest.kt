package com.lalilu.lmedia.source

import com.lalilu.common.kv.KVContext
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.source.subsonic.SubsonicConfig
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientSourceConfigPersistenceTest {
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
    fun `remote and subsonic configurations are restored as complete values`() {
        val first = LMediaKV(InMemoryKVSaver(store))
        val remote = RemoteSourceConfig(
            url = "http://192.168.1.2:7779",
            salt = "remote-salt",
            token = "remote-token",
        )
        val subsonic = SubsonicConfig(
            url = "https://music.example.com/rest/",
            username = "tester",
            salt = "subsonic-salt",
            token = "subsonic-token",
        )

        first.obtain("RemoteSourceConfigTest", RemoteSourceConfig()).value = remote
        first.obtain("SubsonicSourceConfigTest", SubsonicConfig.Empty).value = subsonic

        KVContext.kvMap.clear()
        val restored = LMediaKV(InMemoryKVSaver(store))

        assertEquals(remote, restored.obtain("RemoteSourceConfigTest", RemoteSourceConfig()).value)
        assertEquals(subsonic, restored.obtain("SubsonicSourceConfigTest", SubsonicConfig.Empty).value)
    }
}
