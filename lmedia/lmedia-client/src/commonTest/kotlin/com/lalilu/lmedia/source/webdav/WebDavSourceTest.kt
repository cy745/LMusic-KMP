package com.lalilu.lmedia.source.webdav

import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
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

    private fun newSource(client: WebDavClient): WebDavSource = WebDavSource(
        clientFactory = { client },
        kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
    )

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
            override suspend fun put(path: String, bytes: ByteArray, contentType: String) = Unit
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
    fun `refuses to play before the local proxy is wired`() = runTest {
        val source = newSource(FakeWebDavClient(files))
        source.connect("http://dav.local:8080", "lmusic", "pw", "/Music").getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first()

        // 阶段 2 接入回环代理前，不允许把带凭据的直链交给播放器
        assertNull(source.getMedia(audio))
        source.deactivate()
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

    private suspend fun awaitSnapshot(source: WebDavSource, timeoutMillis: Long = 5_000): Snapshot? =
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) { source.snapshot.first { it != null } }
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
) : WebDavClient {
    private val tree = treeOf(files)
    val propfindCalls = mutableListOf<String>()

    override suspend fun propfind(path: String, depth: Int): List<WebDavEntry> {
        propfindCalls += path
        delay(1)
        failure?.let { throw it }
        if (path in failingDirectories) throw WebDavException.Unexpected("目录不可读：$path")
        return tree[path].orEmpty()
    }

    override suspend fun download(path: String): ByteArray? = null
    override suspend fun put(path: String, bytes: ByteArray, contentType: String) = Unit
    override fun close() = Unit

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
