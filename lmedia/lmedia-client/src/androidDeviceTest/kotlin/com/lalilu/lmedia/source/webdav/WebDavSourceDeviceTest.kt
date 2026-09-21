package com.lalilu.lmedia.source.webdav

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaFetchOptions
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.net.NetworkType
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android 真机/模拟器上的 WebDAV 端到端用例。
 *
 * 与 JVM 用例互补：这里跑的是 Android 运行时，覆盖单测碰不到的三件事——FileKit 的 Android
 * 缓存目录、taglib 的 JNI 绑定、以及"回环代理在真实 Android 网络栈上取字节"。
 *
 * 需要本机起 WebDAV 服务，并且模拟器能通过 `10.0.2.2` 访问宿主（默认值就是它）：
 *
 * ```
 * adb shell am instrument -w -e class com.lalilu.lmedia.source.webdav.WebDavSourceDeviceTest \
 *   com.lalilu.lmedia.client.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * 未配置 `LMUSIC_WEBDAV_URL` 时跳过（与 JVM 侧真实服务用例同一套守卫）。
 */
@OptIn(UnstableApi::class)
class WebDavSourceDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** 模拟器里 `10.0.2.2` 指向宿主的回环地址，因此默认值直接可用。 */
    private val url: String? = System.getenv("LMUSIC_WEBDAV_URL") ?: "http://10.0.2.2:8080"
    private val username: String = System.getenv("LMUSIC_WEBDAV_USERNAME") ?: "lmusic"
    private val password: String = System.getenv("LMUSIC_WEBDAV_PASSWORD") ?: "lmusic-test"
    private val root: String = System.getenv("LMUSIC_WEBDAV_ROOT") ?: "/"

    /** 跳过时带出的原因，便于区分"服务没起"与"网络不通"。 */
    private var reachError: String? = null

    /**
     * `kv.obtain<WebDavConfig>()` 解析 KV 转换器时会向 Koin 取 `Json`，所以构造数据源之前
     * 必须先起 Koin（与 JVM 侧真实服务用例同一套最小模块）。
     */
    @Before
    fun setup() {
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    /**
     * 每个用例（每次运行）一个独立的缓存目录。
     *
     * 设备上的 `cacheDir` 是跨运行保留的：共用目录会让上一轮的"完整提取记录"或半截缓存把
     * 这一轮的提取悄悄跳过，用例随即超时——这类假失败排查起来非常费劲。
     */
    private fun newSource(): WebDavSource {
        val cacheRoot = File(context.cacheDir, "webdav-${System.nanoTime()}")
            .apply { mkdirs() }
            .absolutePath
        return WebDavSource(
            clientFactory = { HttpWebDavClientFactory().create(it) },
            cacheRootProvider = { cacheRoot },
            json = Json { ignoreUnknownKeys = true },
            kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
            networkObservation = { flowOf(NetworkType.WIFI) },
        )
    }

    @Test
    fun scans_plays_through_the_loopback_proxy_and_extracts_real_tags() = runBlocking {
        val reachable = reachable()
        assumeTrue("WebDAV 服务不可达（$reachError），跳过设备用例", reachable)
        val source = newSource()
        source.connect(url!!, username, password, root).getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source), "扫描未产出快照")

        assertEquals(7, snapshot.audios.size, "测试库共 7 首 FLAC：${snapshot.audios.map { it.title }}")
        val friend = snapshot.audios.single { it.title == "Friend" }
        assertTrue(friend.extra?.get("file_size")?.toLongOrNull() ?: 0L > 0L)

        // 1) 播放地址必须是本机回环代理，且不带凭据
        val media = assertNotNull(source.getMedia(friend), "代理未就绪")
        val proxyUrl = (media as MediaData.Url).url
        assertTrue(proxyUrl.startsWith("http://127.0.0.1:"), "必须走本机回环：$proxyUrl")

        // 2) 边车歌词：扫描阶段就能拿到，不需要下载整首
        val lyric = assertNotNull(source.getLyric(friend), "应取到同目录 .lrc")
        assertTrue(lyric.contains("first line"), lyric)

        // 3) 整首走代理：长度与内容都要对
        val patched = async(Dispatchers.Default) {
            withTimeout(120_000) {
                source.audioPatches.first {
                    it.id == friend.id && it.extra?.get(LAudioExtraKeys.Duration) != null
                }
            }
        }
        val whole = httpGet(proxyUrl, range = null)
        assertEquals(200, whole.status, "整文件请求应返回 200")
        assertEquals(
            friend.extra?.get("file_size")?.toLongOrNull(),
            whole.body.size.toLong(),
            "代理返回的字节数应与服务器声明一致",
        )

        // 4) 整首到齐后应当提取出真实标签（Android 上走 taglib JNI）
        val patch = patched.await()
        assertTrue(patch.title.isNotBlank())
        val duration = patch.extra?.get(LAudioExtraKeys.Duration)?.toLongOrNull() ?: 0L
        assertTrue(duration > 0L, "完整缓存后应有时长：${patch.extra}")

        val store = assertNotNull(source.metadataStoreOrNull, "提取存储应已装配")
        val key = WebDavSource.cacheKeyOf(friend.id)
        val record = assertNotNull(store.read(key), "提取记录应落在本地缓存目录")
        assertTrue(record.complete)
        assertEquals(duration, record.duration)
        assertNotNull(store.readCover(key), "内嵌封面应落盘")

        // 5) seek：中间区间必须与前缀一致
        val middle = httpGet(proxyUrl, range = "bytes=1000-2000")
        assertEquals(206, middle.status, "区间请求应返回 206")
        assertContentEquals(whole.body.copyOfRange(1000, 2001), middle.body)

        source.deactivate()
    }

    /**
     * 真实播放链路：把数据源给出的回环代理地址交给 Media3/ExoPlayer 播放，并拖动进度。
     *
     * 上一个用例保证 HTTP 层的字节一致；这条回答"能不能听"——播放器真的在解码代理喂给它的
     * 字节，且 seek 触发的 Range 请求也被正确处理。
     */
    @Test
    fun plays_the_loopback_proxy_url_through_media3_and_seeks() = runBlocking {
        val reachable = reachable()
        assumeTrue("WebDAV 服务不可达（$reachError），跳过设备用例", reachable)
        val source = newSource()
        source.connect(url!!, username, password, root).getOrThrow()
        val friend = assertNotNull(awaitSnapshot(source)).audios.single { it.title == "Friend" }
        val proxyUrl = (assertNotNull(source.getMedia(friend)) as MediaData.Url).url

        lateinit var player: ExoPlayer
        onMain {
            player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(proxyUrl))
                prepare()
                play()
            }
        }

        try {
            val started = awaitPosition(player) { it > 0L }
            assertTrue(started > 0L, "播放器应开始播放代理地址：position=$started")

            // 向前拖动：会触发 Range 请求，代理需要补齐中间缺口
            val target = 15_000L
            onMain { player.seekTo(target) }
            val seeked = awaitPosition(player) { it >= target - 1_000L }
            assertTrue(
                seeked >= target - 1_000L,
                "拖动到 ${target}ms 后播放位置应跟进：position=$seeked",
            )

            // 暂停期间位置不再前进、继续后恢复前进（"暂停/继续不报错"这条验收项）
            onMain { player.pause() }
            val pausedAt = currentPosition(player)
            kotlinx.coroutines.delay(1_000)
            val stillPaused = currentPosition(player)
            assertTrue(
                stillPaused - pausedAt < 500L,
                "暂停期间位置不应继续前进：$pausedAt → $stillPaused",
            )

            onMain { player.play() }
            val resumed = awaitPosition(player) { it > stillPaused + 200L }
            assertTrue(resumed > stillPaused, "继续播放后位置应前进：$stillPaused → $resumed")
        } finally {
            onMain { player.release() }
            source.deactivate()
        }
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun currentPosition(player: ExoPlayer): Long {
        var position = -1L
        onMain { position = player.currentPosition }
        return position
    }

    /** 轮询播放位置；超时返回最后一次读数，由调用方断言。 */
    private suspend fun awaitPosition(
        player: ExoPlayer,
        timeoutMillis: Long = 20_000,
        predicate: (Long) -> Boolean,
    ): Long {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var position = currentPosition(player)
        while (System.currentTimeMillis() < deadline) {
            if (predicate(position)) return position
            kotlinx.coroutines.delay(200)
            position = currentPosition(player)
        }
        return position
    }

    /** 服务不可达时跳过，而不是让用例以一个看不懂的异常失败。 */
    private suspend fun reachable(): Boolean = withContext(Dispatchers.IO) {
        val uri = URI(url!!)
        val port = if (uri.port > 0) uri.port else 80
        runCatching {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(uri.host, port), 3_000)
            }
        }.fold(
            onSuccess = { true },
            onFailure = {
                // 原因带进跳过信息：排查"这条用例为什么没跑"时不用再猜
                reachError = "${uri.host}:$port -> ${it.message ?: it::class.simpleName}"
                false
            },
        )
    }

    private suspend fun awaitSnapshot(source: WebDavSource, timeoutMillis: Long = 30_000): Snapshot? =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) { source.snapshot.first { it != null } }
        }

    private suspend fun httpGet(url: String, range: String?): DeviceResponse =
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            range?.let { connection.setRequestProperty("Range", it) }
            val status = connection.responseCode
            val body = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            DeviceResponse(status = status, body = body)
        }

    private class DeviceResponse(val status: Int, val body: ByteArray)
}
