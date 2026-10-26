package com.lalilu.lplayer.playback

import com.lalilu.lplayer.core.verifyNativeWebDavLifecycle
import com.lalilu.lplayer.player.VLCPlayerLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.nio.file.Files
import kotlin.test.Test

class NativeWebDavLifecycleTest {
    @Test fun realDavScanPlaybackSeekShuffleAndOfflineCatalogRestore() = runBlocking {
        val url = System.getenv("LMUSIC_WEBDAV_URL")
        val resources = System.getenv("LMUSIC_NATIVE_RESOURCES")
        assumeTrue(url != null && resources != null)
        System.setProperty("compose.application.resources.dir", resources!!)
        VLCPlayerLoader.initialize().await()
        val factory = MediaPlayerFactory(null as NativeDiscovery?, "--no-video")
        val root = Files.createTempDirectory("native-webdav-lifecycle").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
        try {
            verifyNativeWebDavLifecycle(root.path, url!!, System.getenv("LMUSIC_WEBDAV_USERNAME").orEmpty(),
                System.getenv("LMUSIC_WEBDAV_PASSWORD").orEmpty(), scope, { HttpClient(OkHttp) },
                { VlcPcmEngine(scope, { factory.mediaPlayers().newMediaPlayer() }, it) })
        } finally { scope.cancel(); factory.release(); root.deleteRecursively(); stopKoin() }
    }
}
