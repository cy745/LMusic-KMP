package com.lalilu.lmedia.source.webdav

import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaFetchOptions
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 扫描链路的覆盖测试：递归、过滤、派生、ID 稳定性、单目录失败容错与进度上报。
 *
 * 用假客户端而不是 HTTP 模拟，是因为这里要验证的是扫描语义；HTTP 细节由
 * [HttpWebDavClientTest] 单独覆盖。
 */
class WebDavSourceTest {

    private val files = listOf(
        "/Music/HoneyComeBear/02 Friend.flac",
        "/Music/HoneyComeBear/Daisy Crown/03 さよならの支度.flac",
        "/Music/MyGO!!!!!/05 ノンブレス・オブリージュ (Cover).flac",
        "/Music/not-music/readme.txt",
        "/Music/cover.jpg",
    )

    @BeforeTest
    fun setup() {
        startKoin { modules(module { single { Json { ignoreUnknownKeys = true } } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun newSource(
        client: WebDavClient,
        cacheRoot: String? = freshRoot("default"),
    ): WebDavSource = WebDavSource(
        clientFactory = { client },
        cacheRootProvider = { cacheRoot },
        json = Json { ignoreUnknownKeys = true },
        kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
    )

    /**
     * 每个用例（以及每次运行）一个全新的缓存目录。
     *
     * 共用目录会让"本地已缓存"的断言被上一次运行的残留悄悄满足——例如边车文件的
     * "第二次不再下载"、同步的"拉回几条"，都会在第二次运行时变成假通过。
     */
    private fun freshRoot(name: String): String = "build/test-webdav-source/$name-${Random.nextLong()}"

    @Test
    fun `scans the whole tree and maps only audio files into the snapshot`() = runTest {
        val client = FakeWebDavClient(files)
        val source = newSource(client)

        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source))

        assertEquals(3, snapshot.audios.size)
        assertTrue(snapshot.audios.all { it.mediaSourceName == "WebDavSource" })
        assertTrue(snapshot.audios.all { it.id.startsWith("audio_") })

        // 排序：歌手 → 专辑 → 歌名，保证界面顺序稳定
        val titles = snapshot.audios.map { it.title }
        assertEquals(
            listOf("さよならの支度", "Friend", "ノンブレス・オブリージュ (Cover)"),
            titles,
        )

        val friend = snapshot.audios.first { it.title == "Friend" }
        assertEquals("HoneyComeBear", friend.artistName)
        assertEquals("HoneyComeBear", friend.albumName)
        assertEquals("2", friend.extra?.get("track"))
        assertEquals("/Music/HoneyComeBear/02 Friend.flac", friend.extra?.get(WebDavSource.EXTRA_PATH))
        assertEquals("/Music/HoneyComeBear", friend.extra?.get(WebDavSource.EXTRA_DIR_PATH))
        assertEquals("1234", friend.extra?.get(WebDavSource.EXTRA_FILE_SIZE))
        assertNotNull(friend.extra?.get(WebDavSource.EXTRA_ETAG))

        val nested = snapshot.audios.first { it.title == "さよならの支度" }
        assertEquals("HoneyComeBear", nested.artistName)
        assertEquals("Daisy Crown", nested.albumName)

        // 目录自身条目与非音频文件都不进快照
        assertTrue(snapshot.audios.none { it.title == "Music" })
        assertTrue(snapshot.audios.none { it.title.contains("readme") })
        assertTrue(snapshot.audios.none { it.title.contains("cover") })

        // 每个目录只查询一次
        assertEquals(client.propfindCalls.size, client.propfindCalls.distinct().size)
        source.deactivate()
    }

    @Test
    fun `keeps scanning when a single directory fails`() = runTest {
        val client = FakeWebDavClient(files, failingDirectories = setOf("/Music/MyGO!!!!!/"))
        val source = newSource(client)

        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source))

        assertEquals(2, snapshot.audios.size)
        assertTrue(snapshot.audios.none { it.title.contains("ノンブレス") })
        assertFalse(source.state.value is SnapshotState.Error)
        source.deactivate()
    }

    @Test
    fun `produces stable ids across repeated scans`() = runTest {
        val first = newSource(FakeWebDavClient(files))
        first.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val firstIds = assertNotNull(awaitSnapshot(first)).audios.map { it.id }.sorted()
        first.deactivate()

        val second = newSource(FakeWebDavClient(files))
        second.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val secondIds = assertNotNull(awaitSnapshot(second)).audios.map { it.id }.sorted()
        second.deactivate()

        assertEquals(firstIds, secondIds)
        assertEquals(3, firstIds.size)
    }

    @Test
    fun `reports the failure message when the server rejects the credentials`() = runTest {
        val client = FakeWebDavClient(
            files = files,
            failure = WebDavException.Unauthorized("认证失败：请检查用户名与应用专用密码"),
        )
        val source = newSource(client)

        source.connect("http://dav.local:8080", "lmusic", "wrong", "/Music").getOrThrow()
        val state = awaitTerminal(source)

        assertTrue(state is SnapshotState.Error, "期望失败状态，实际 $state")
        assertTrue(
            (state as SnapshotState.Error).message.contains("认证失败"),
            "错误信息应保留服务端语义：${state.message}",
        )
        assertNull(source.snapshot.value)
        source.deactivate()
    }

    @Test
    fun `reports directory progress while scanning`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val tree = FakeWebDavClient.treeOf(files)
        val client = object : WebDavClient {
            override suspend fun propfind(path: String, depth: Int): List<WebDavEntry> {
                calls += 1
                // 第二层挂起，留出稳定观察首次进度消息的窗口
                if (calls == 2) gate.await()
                return tree[path].orEmpty()
            }

            override suspend fun download(path: String): ByteArray? = null
            override suspend fun fetchRange(
                path: String,
                start: Long,
                endInclusive: Long?,
                onChunk: suspend (ByteArray) -> Unit,
            ): Long? = null

            override suspend fun put(path: String, bytes: ByteArray, contentType: String) = Unit

            override suspend fun mkcol(path: String): Boolean = true

            override fun close() = Unit
        }
        val source = newSource(client)
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()

        val loading = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                source.state.first { it is SnapshotState.Loading && it.message.contains("扫描目录") }
            }
        }
        assertTrue((loading as SnapshotState.Loading).message.contains("发现"))

        gate.complete(Unit)
        assertNotNull(awaitSnapshot(source))
        source.deactivate()
    }

    @Test
    fun `plays through the loopback proxy without exposing credentials`() = runTest {
        val source = newSource(FakeWebDavClient(files))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first()

        val media = assertNotNull(source.getMedia(audio), "代理启动后应能给出播放地址")
        val url = (media as MediaData.Url).url
        assertTrue(url.startsWith("http://127.0.0.1:"), "播放地址必须是本机回环：$url")
        assertTrue(url.contains("/audio/"), "播放地址必须走代理路由：$url")
        assertFalse(url.contains("pw"), "凭据不能出现在播放地址里：$url")
        assertFalse(url.contains("dav.local"), "播放地址不能直接指向远端：$url")

        source.deactivate()
    }

    @Test
    fun `refuses to connect on a platform without a cache directory`() = runTest {
        val source = newSource(FakeWebDavClient(files), cacheRoot = null)

        val failure = source.connect("http://dav.local:8080", "lmusic", "pw", "/Music")

        assertTrue(failure.isFailure)
        assertEquals("当前平台不支持 WebDAV 数据源", failure.exceptionOrNull()?.message)

        source.init()
        val content = source.contentState.value
        assertTrue(
            content.availability is MediaContentAvailability.Unavailable,
            "平台不支持时内容状态必须是不可用，实际 $content",
        )
    }

    @Test
    fun `returns failure when no password can be reused for a different server`() = runTest {
        val source = newSource(FakeWebDavClient(files))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        assertNotNull(awaitSnapshot(source))

        val failure = source.connect("http://other.local:8080", "lmusic", "", "/Music")
        assertTrue(failure.isFailure)
        assertEquals("请输入密码", failure.exceptionOrNull()?.message)
        source.deactivate()
    }

    @Test
    fun `derives cover and lyric sidecars from the same directory listing`() = runTest {
        val client = FakeWebDavClient(
            files = files + listOf(
                "/Music/HoneyComeBear/cover.jpg",
                "/Music/HoneyComeBear/02 Friend.lrc",
                // 既不是约定名、目录里又不只一张图 → 不允许随便挑一张当封面
                "/Music/HoneyComeBear/back.jpg",
            ),
        )
        val source = newSource(client, cacheRoot = freshRoot("sidecar-scan"))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source))

        val friend = snapshot.audios.first { it.title == "Friend" }
        assertEquals("/Music/HoneyComeBear/cover.jpg", friend.extra?.get(WebDavSource.EXTRA_COVER_PATH))
        assertNotNull(friend.extra?.get(WebDavSource.EXTRA_COVER_FINGERPRINT))
        assertEquals("/Music/HoneyComeBear/02 Friend.lrc", friend.extra?.get(WebDavSource.EXTRA_LYRIC_PATH))

        // 目录里出现 second 图片后就只剩"约定名"这一条规则
        val nested = snapshot.audios.first { it.title == "さよならの支度" }
        assertNull(nested.extra?.get(WebDavSource.EXTRA_COVER_PATH), "没有约定名封面时不应把其它图当封面")
        assertNull(nested.extra?.get(WebDavSource.EXTRA_LYRIC_PATH))
        source.deactivate()
    }

    @Test
    fun `serves sidecar cover and lyric and caches them locally`() = runTest {
        val coverBytes = byteArrayOf(11, 22, 33)
        val lyricText = "[00:01.00]第一句\n[00:02.00]第二句"
        val client = FakeWebDavClient(
            files = files + listOf("/Music/HoneyComeBear/cover.jpg", "/Music/HoneyComeBear/02 Friend.lrc"),
            payloads = mutableMapOf(
                "/Music/HoneyComeBear/cover.jpg" to coverBytes,
                "/Music/HoneyComeBear/02 Friend.lrc" to lyricText.encodeToByteArray(),
            ),
        )
        val source = newSource(client, cacheRoot = freshRoot("sidecar-serve"))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val friend = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }

        val picture = assertNotNull(source.getPicture(friend, MediaFetchOptions()), "应能取到同目录封面")
        assertContentEquals(coverBytes, (picture as MediaData.Bytes).bytes)
        assertEquals(lyricText, source.getLyric(friend), "同名 .lrc 应作为歌词返回")

        val downloadsAfterFirst = client.downloads.size
        source.getPicture(friend, MediaFetchOptions())
        source.getLyric(friend)
        assertEquals(downloadsAfterFirst, client.downloads.size, "边车文件应命中本地缓存，不再请求远端")

        source.deactivate()
    }

    @Test
    fun `returns no picture and no lyric when the track has neither`() = runTest {
        val source = newSource(FakeWebDavClient(files), cacheRoot = freshRoot("sidecar-empty"))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }

        assertNull(source.getPicture(audio, MediaFetchOptions()))
        // 没有边车、音频也没缓存到本地 → 没有歌词可读
        assertNull(source.getLyric(audio))
        source.deactivate()
    }

    @Test
    fun `uploads local metadata when sync is switched on`() = runTest {
        val client = FakeWebDavClient(files)
        val source = newSource(client, cacheRoot = freshRoot("sync-upload"))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }
        val key = WebDavSource.cacheKeyOf(audio.id)

        // 等价于"播放顺路提取完成"：本地已有一条记录
        assertNotNull(source.metadataStoreOrNull).write(
            key,
            WebDavMetadataRecord(
                fingerprint = "1234|\"tag\"",
                complete = true,
                extractedAt = 1,
                title = "Friend",
            ),
            byteArrayOf(1, 2),
        )

        source.updateOptions(metaSyncEnabled = true).getOrThrow()
        val state = awaitSyncSettled(source)

        assertTrue(state is WebDavMetadataSyncState.Synced, "同步应成功，实际 $state")
        assertTrue(client.mkcols.contains("/.lmusic/"), "应创建元数据根目录：${client.mkcols}")
        assertTrue(
            client.puts.any { it.first == "/.lmusic/$key.json" },
            "应上传记录 json：${client.puts.map { it.first }}",
        )
        assertTrue(
            client.puts.any { it.first == "/.lmusic/$key.cover" },
            "应同时上传封面：${client.puts.map { it.first }}",
        )
        source.deactivate()
    }

    @Test
    fun `reports a read-only sync target without failing the extraction pipeline`() = runTest {
        val client = FakeWebDavClient(files, readOnlyPrefixes = setOf("/.lmusic/"))
        val source = newSource(client, cacheRoot = freshRoot("sync-readonly"))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        assertNotNull(awaitSnapshot(source))

        source.updateOptions(metaSyncEnabled = true).getOrThrow()
        val state = awaitSyncSettled(source)

        assertTrue(state is WebDavMetadataSyncState.Failed, "只读目标必须给出失败状态，实际 $state")
        assertTrue(
            (state as WebDavMetadataSyncState.Failed).readOnly,
            "应区分「只读/无权限」与其它失败，才能给出正确提示",
        )
        assertTrue(client.puts.isEmpty())
        assertFalse(source.state.value is SnapshotState.Error, "同步失败不能影响媒体库状态")
        source.deactivate()
    }

    @Test
    fun `pulls remote metadata into the local cache on sync`() = runTest {
        val record = WebDavMetadataRecord(
            fingerprint = "1234|\"tag\"",
            complete = true,
            extractedAt = 42,
            title = "远端标题",
        )
        val path = "/.lmusic/friend.json"
        val client = FakeWebDavClient(
            files = files,
            extraTree = mapOf(
                "/.lmusic/" to listOf(
                    WebDavEntry(path = "/.lmusic/", isDirectory = true),
                    WebDavEntry(path = path, isDirectory = false, contentLength = 10L, etag = "\"m1\""),
                ),
            ),
            payloads = mutableMapOf(path to Json.encodeToString(record).encodeToByteArray()),
        )
        val root = freshRoot("sync-pull")
        val source = newSource(client, cacheRoot = root)
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        assertNotNull(awaitSnapshot(source))

        val remote = HttpWebDavMetadataRemote(client, "/.lmusic/", "/.lmusic/")
        assertEquals(setOf("friend"), remote.keys(), "propfind=${client.propfindCalls}")

        source.updateOptions(metaSyncEnabled = true).getOrThrow()
        val state = awaitSyncSettled(source)

        assertTrue(
            state is WebDavMetadataSyncState.Synced && state.pulled == 1,
            "同步结果 $state；propfind=${client.propfindCalls}；mkcol=${client.mkcols}；" +
                "downloads=${client.downloads}",
        )

        // 换一个没有远端层的 store 读同一个缓存目录：能读到才说明回写本地了
        val offline = WebDavMetadataStore(
            cacheRoot = root,
            json = Json { ignoreUnknownKeys = true },
        )
        assertEquals("远端标题", assertNotNull(offline.read("friend")).title)
        source.deactivate()
    }

    private suspend fun awaitSnapshot(source: WebDavSource, timeoutMillis: Long = 5_000): Snapshot? =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) { source.snapshot.first { it != null } }
        }

    private suspend fun awaitSyncSettled(
        source: WebDavSource,
        timeoutMillis: Long = 5_000,
    ): WebDavMetadataSyncState = withContext(Dispatchers.Default) {
        withTimeout(timeoutMillis) {
            source.metadataSyncState.first {
                it is WebDavMetadataSyncState.Synced || it is WebDavMetadataSyncState.Failed
            }
        }
    }

    private suspend fun awaitTerminal(source: WebDavSource, timeoutMillis: Long = 5_000): SnapshotState =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) {
                source.state.first { it is SnapshotState.Success || it is SnapshotState.Error }
            }
        }
}

private class FakeWebDavClient(
    files: List<String>,
    private val failingDirectories: Set<String> = emptySet(),
    private val failure: Throwable? = null,
    /** 追加的目录条目（例如远端元数据目录），用于同步相关用例。 */
    extraTree: Map<String, List<WebDavEntry>> = emptyMap(),
    /** 可下载的静态内容（边车封面/歌词、远端元数据 json）。 */
    val payloads: MutableMap<String, ByteArray> = mutableMapOf(),
    /** 这些前缀下的写操作一律 403，用来模拟只读挂载。 */
    private val readOnlyPrefixes: Set<String> = emptySet(),
) : WebDavClient {
    private val tree = treeOf(files) + extraTree
    val propfindCalls = mutableListOf<String>()
    val fetchedRanges = mutableListOf<Pair<Long, Long?>>()
    val downloads = mutableListOf<String>()
    val puts = mutableListOf<Pair<String, ByteArray>>()
    val mkcols = mutableListOf<String>()

    override suspend fun propfind(path: String, depth: Int): List<WebDavEntry> {
        propfindCalls += path
        delay(1)
        failure?.let { throw it }
        if (path in failingDirectories) throw WebDavException.Unexpected("目录不可读：$path")
        return tree[path].orEmpty()
    }

    override suspend fun download(path: String): ByteArray? {
        downloads += path
        payloads[path]?.let { return it }
        if (tree.values.flatten().any { it.path == path && !it.isDirectory }) {
            throw WebDavException.NotFound("未提供内容的测试文件：$path")
        }
        return null
    }

    override suspend fun fetchRange(
        path: String,
        start: Long,
        endInclusive: Long?,
        onChunk: suspend (ByteArray) -> Unit,
    ): Long? {
        fetchedRanges += start to endInclusive
        return null
    }

    override suspend fun put(path: String, bytes: ByteArray, contentType: String) {
        ensureWritable(path)
        puts += path to bytes
        payloads[path] = bytes
    }

    override suspend fun mkcol(path: String): Boolean {
        ensureWritable(path)
        mkcols += path
        return true
    }

    override fun close() = Unit

    private fun ensureWritable(path: String) {
        if (readOnlyPrefixes.any { path.startsWith(it) }) {
            throw WebDavException.Forbidden("没有访问权限：请检查该账号对目标目录的权限")
        }
    }

    companion object {
        /** 按真实服务器行为展开：每个目录的响应都包含自身条目、直接子目录与其中的文件。 */
        fun treeOf(files: List<String>): Map<String, List<WebDavEntry>> {
            val directories = sortedSetOf("/")
            files.forEach { file ->
                var current = "/"
                file.trim('/').split('/').dropLast(1).forEach { segment ->
                    current = if (current == "/") "/$segment/" else "$current$segment/"
                    directories += current
                }
            }

            return directories.associateWith { directory ->
                val self = WebDavEntry(path = directory, isDirectory = true)
                val subDirectories = directories
                    .filter { it != directory && it.startsWith(directory) && parentOf(it) == directory }
                    .map { WebDavEntry(path = it, isDirectory = true) }
                val here = files
                    .filter { parentOf(it) == directory }
                    .map {
                        WebDavEntry(
                            path = it,
                            isDirectory = false,
                            contentLength = 1234L,
                            etag = "\"tag-${it.hashCode()}\"",
                            lastModified = "Wed, 24 Sep 2025 05:14:28 GMT",
                            contentType = "audio/flac",
                        )
                    }
                listOf(self) + subDirectories + here
            }
        }

        private fun parentOf(path: String): String {
            val segments = path.trim('/').split('/').dropLast(1).filter(String::isNotBlank)
            return if (segments.isEmpty()) "/" else "/${segments.joinToString("/")}/"
        }
    }
}
