package com.lalilu.lmedia.source.webdav

import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 对着**真实 WebDAV 服务**的全链路用例。
 *
 * 未配置环境变量时整体跳过（与原生播放用例的环境守卫一致），不让 CI 依赖外部服务：
 *
 * ```
 * LMUSIC_WEBDAV_URL=http://127.0.0.1:8080 \
 * LMUSIC_WEBDAV_USERNAME=lmusic \
 * LMUSIC_WEBDAV_PASSWORD=... \
 * LMUSIC_WEBDAV_ROOT=/ \
 * ./gradlew :lmedia:lmedia-client:jvmTest --tests "*WebDavLiveServerTest*"
 * ```
 */
class WebDavLiveServerTest {

    private val url: String? = System.getenv("LMUSIC_WEBDAV_URL")
    private val username: String = System.getenv("LMUSIC_WEBDAV_USERNAME").orEmpty()
    private val password: String = System.getenv("LMUSIC_WEBDAV_PASSWORD").orEmpty()
    private val root: String = System.getenv("LMUSIC_WEBDAV_ROOT") ?: "/"

    private val config: WebDavConfig
        get() = WebDavConfig(
            url = url.orEmpty(),
            username = username,
            password = password,
            rootPath = root,
        )

    @BeforeTest
    fun setup() {
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `propfind lists the real library tree with etags and sizes`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val client = HttpWebDavClient(config)

        val rootEntries = client.propfind(root, depth = 1)
        val directories = rootEntries.filter { it.isDirectory && it.path != normalizeDirectoryPath(root) }
        assertTrue(
            directories.any { it.path.contains("HoneyComeBear") },
            "库根目录应列出艺人子目录：${rootEntries.map { it.path }}",
        )
        assertTrue(
            rootEntries.any { it.isDirectory && it.path.contains(".lmusic") },
            "测试库应包含元数据目录：${rootEntries.map { it.path }}",
        )

        // 走进艺人目录：文件条目必须带 getcontentlength / getetag，否则后续增量对账无法判断变更
        val artistDirectory = directories.first { it.path.contains("HoneyComeBear") }
        val files = client.propfind(artistDirectory.path, depth = 1).filterNot { it.isDirectory }
        assertTrue(files.isNotEmpty(), "艺人目录下应有音频文件")
        assertTrue(
            files.all { it.contentLength > 0L && it.etag != null && !it.contentType.isNullOrBlank() },
            "文件条目应包含大小、etag 与内容类型：$files",
        )
        client.close()
    }

    @Test
    fun `scans the real server into a snapshot with derived metadata`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = WebDavSource(
            clientFactory = { HttpWebDavClientFactory().create(it) },
            cacheRootProvider = { System.getProperty("java.io.tmpdir") },
            kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
        )

        source.connect(url!!, username, password, root).getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source), "扫描未产出快照")

        assertEquals(7, snapshot.audios.size, "测试库共 7 首 FLAC：${snapshot.audios.map { it.title }}")
        assertTrue(
            snapshot.audios.none { it.title.contains("readme") },
            "非音频文件不应进入媒体库",
        )

        val friend = snapshot.audios.single { it.title == "Friend" }
        assertEquals("HoneyComeBear", friend.artistName)
        assertEquals("HoneyComeBear", friend.albumName)
        assertEquals("2", friend.extra?.get("track"))
        assertTrue((friend.extra?.get("file_size")?.toLongOrNull() ?: 0L) > 0L)
        assertNotNull(friend.extra?.get("etag"))
        assertTrue(
            friend.extra?.get("path")?.endsWith("02 Friend.flac") == true,
            "extra.path 应为解码后的真实路径：${friend.extra?.get("path")}",
        )

        // 中文/多级目录：库根 → 艺人目录 → 曲目
        val japanese = snapshot.audios.single { it.title.contains("風のたより") }
        assertEquals("tayori", japanese.artistName)

        source.deactivate()
    }

    @Test
    fun `round trips a metadata file on the real server`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val client = HttpWebDavClient(config)
        val path = "/.lmusic/live-probe.json"
        val payload = """{"probe":"lmusic"}"""

        client.put(path, payload.encodeToByteArray(), "application/json")
        val readBack = client.download(path)
        client.close()

        assertEquals(payload, readBack?.decodeToString(), "写入的内容应能原样读回")
    }

    @Test
    fun `streams a real track through the loopback proxy byte for byte`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = WebDavSource(
            clientFactory = { HttpWebDavClientFactory().create(it) },
            cacheRootProvider = { System.getProperty("java.io.tmpdir") },
            kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
        )
        source.connect(url!!, username, password, root).getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }

        // 直连取一份基准字节（本地容器，几秒内完成）
        val directClient = HttpWebDavClient(config)
        val expected = assertNotNull(directClient.download(audio.extra?.get("path").orEmpty()))
        directClient.close()

        val media = assertNotNull(source.getMedia(audio), "代理未就绪，无法给出播放地址")
        val proxyUrl = (media as MediaData.Url).url
        assertTrue(proxyUrl.startsWith("http://127.0.0.1:"), "必须走本机代理：$proxyUrl")

        // 1) 中间区间：与直连字节逐字节一致
        val middle = httpGet(proxyUrl, "bytes=1000-2000")
        assertEquals(206, middle.status)
        assertEquals("bytes 1000-2000/${expected.size}", middle.contentRange)
        assertContentEquals(expected.copyOfRange(1000, 2001), middle.body)

        // 2) 整文件：200 + 完整内容（同时把缓存补满）
        val whole = httpGet(proxyUrl, null)
        assertEquals(200, whole.status)
        assertContentEquals(expected, whole.body)

        // 3) 缓存已满之后，重复请求仍然正确
        val again = httpGet(proxyUrl, "bytes=1000-2000")
        assertEquals(206, again.status)
        assertContentEquals(expected.copyOfRange(1000, 2001), again.body)

        source.deactivate()
    }

    private suspend fun httpGet(url: String, range: String?): LiveResponse = withContext(Dispatchers.IO) {
        val builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
        if (range != null) builder.header("Range", range)
        val response = java.net.http.HttpClient.newHttpClient()
            .send(builder.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
        LiveResponse(
            status = response.statusCode(),
            body = response.body(),
            contentRange = response.headers().firstValue("Content-Range").orElse(null),
        )
    }

    private class LiveResponse(
        val status: Int,
        val body: ByteArray,
        val contentRange: String?,
    )

    private suspend fun awaitSnapshot(source: WebDavSource, timeoutMillis: Long = 30_000): Snapshot? =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) { source.snapshot.first { it != null } }
        }
}
