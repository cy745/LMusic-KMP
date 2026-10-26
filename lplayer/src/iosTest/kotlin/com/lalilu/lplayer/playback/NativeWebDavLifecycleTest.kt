package com.lalilu.lplayer.playback

import com.lalilu.lplayer.core.verifyNativeWebDavLifecycle
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import platform.Foundation.*
import platform.posix.getenv
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
class NativeWebDavLifecycleTest {
    @Test fun realDavScanPlaybackSeekShuffleAndOfflineCatalogRestore() {
        val url = getenv("LMUSIC_WEBDAV_URL")?.toKString() ?: return
        val root = NSTemporaryDirectory() + "webdav-lifecycle-${Random.nextLong()}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
        val result = scope.async {
            verifyNativeWebDavLifecycle(root, url, getenv("LMUSIC_WEBDAV_USERNAME")?.toKString().orEmpty(),
                getenv("LMUSIC_WEBDAV_PASSWORD")?.toKString().orEmpty(), scope, { HttpClient(Darwin) },
                { AVPlayerEngine(it).apply { setVolume(0f) } })
        }
        try {
            val started = TimeSource.Monotonic.markNow()
            while (!result.isCompleted && started.elapsedNow() < 150.seconds) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            }
            assertTrue(result.isCompleted, "Live DAV lifecycle timed out")
            runBlocking { result.await() }
        } finally {
            scope.cancel(); NSFileManager.defaultManager.removeItemAtPath(root, null); stopKoin()
        }
    }
}
