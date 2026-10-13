package com.lalilu.lmedia.source.webdav

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 提取结果存储：元数据 json + 封面字节、指纹失效判断，以及**本地 → 远端**的三层读取。
 *
 * 远端用内存假实现，因此这里验证的是读取顺序、回写与冲突取舍这些语义；真实 PUT/GET 由
 * [WebDavLiveServerTest] 对着真服务器验证。
 */
class WebDavMetadataStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val errors = mutableListOf<Throwable>()

    /**
     * 每个用例一个全新的缓存目录：`localKeys()` 这类断言依赖目录内容，
     * 共用目录会让同一用例（或上一次运行）的残留把结果搅乱。
     */
    private fun freshRoot(name: String): String = "build/test-webdav-metadata/$name-${Random.nextLong()}"

    private fun newStore(
        root: String,
        remote: WebDavMetadataRemote? = null,
    ): WebDavMetadataStore = WebDavMetadataStore(
        cacheRoot = root,
        json = json,
        remoteProvider = { remote },
        onRemoteError = { errors += it },
    ).also { it.ensureReady() }

    private fun record(
        fingerprint: String = "1000|\"etag\"",
        complete: Boolean = true,
        extractedAt: Long = 1_700_000_000_000,
    ) = WebDavMetadataRecord(
        fingerprint = fingerprint,
        complete = complete,
        extractedAt = extractedAt,
        title = "Friend",
        artist = "HoneyComeBear",
        album = "Daisy Crown",
        albumArtist = "HoneyComeBear",
        track = "2",
        disc = "1",
        date = "2025",
        genre = "J-Pop",
        duration = 245_000,
        hasCover = true,
    )

    @Test
    fun `round trips a record and its cover`() = runTest {
        val key = "round-trip"
        val store = newStore(root = freshRoot("round-trip"))
        store.write(key, record(), byteArrayOf(1, 2, 3, 4))

        val restored = assertNotNull(store.read(key))
        assertEquals("Friend", restored.title)
        assertEquals("HoneyComeBear", restored.albumArtist)
        assertEquals(245_000L, restored.duration)
        assertTrue(restored.complete)
        assertTrue(restored.hasCover)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), store.readCover(key))
    }

    @Test
    fun `has respects the fingerprint and the completeness requirement`() = runTest {
        val key = "fingerprint-check"
        val store = newStore(root = freshRoot("fingerprint"))
        store.write(key, record(complete = false), null)

        assertTrue(store.has(key, "1000|\"etag\""))
        assertFalse(store.has(key, "2000|\"etag\""))
        assertFalse(
            store.has(key, "1000|\"etag\"", requireComplete = true),
            "只有部分提取的记录不满足「已完整提取」",
        )

        store.write(key, record(complete = true), null)
        assertTrue(store.has(key, "1000|\"etag\"", requireComplete = true))
    }

    @Test
    fun `returns null for unknown keys and missing covers`() = runTest {
        val store = newStore(root = freshRoot("unknown"))
        assertNull(store.read("never-written"))
        assertNull(store.readCover("never-written"))
        assertFalse(store.has("never-written", "any"))
    }

    @Test
    fun `delete removes both the record and the cover`() = runTest {
        val key = "delete-me"
        val store = newStore(root = freshRoot("delete"))
        store.write(key, record(), byteArrayOf(9, 9))

        store.delete(key)

        assertNull(store.read(key))
        assertNull(store.readCover(key))
    }

    @Test
    fun `maps the record back to the shared sparse extra keys`() {
        val extra = record().toAudioExtra(sourceExtra = mapOf("path" to "/music/a.flac"))

        assertEquals("/music/a.flac", extra["path"])
        assertEquals("HoneyComeBear", extra["artistName"])
        assertEquals("Daisy Crown", extra["albumName"])
        assertEquals("2", extra["track"])
        assertEquals("245000", extra["duration"])
    }

    @Test
    fun `omits blank and zero fields when mapping to extra`() {
        val extra = WebDavMetadataRecord(
            fingerprint = "1|",
            complete = false,
            extractedAt = 0,
        ).toAudioExtra(sourceExtra = emptyMap())

        assertTrue(extra.isEmpty(), "空记录不应写入任何字段，避免覆盖文件名派生的信息：$extra")
    }

    // ── 远端层 ──

    @Test
    fun `local hit wins and never touches the remote`() = runTest {
        val key = "local-first"
        val remote = FakeRemote()
        val store = newStore(root = freshRoot("local-first"), remote = remote)
        store.write(key, record(extractedAt = 500), null)
        remote.reset()

        val found = assertNotNull(store.read(key))

        assertEquals(500L, found.extractedAt)
        assertEquals(0, remote.reads, "本地已有记录时不应再查远端")
    }

    @Test
    fun `remote hit is written back to the local cache`() = runTest {
        val key = "pull-from-remote"
        val remote = FakeRemote()
        remote.records[key] = json.encodeToString(record(extractedAt = 900)).encodeToByteArray()

        val root = freshRoot("pull")
        assertEquals(900L, assertNotNull(newStore(root = root, remote = remote).read(key)).extractedAt)
        assertEquals(1, remote.reads)

        // 换一个"没有远端"的 store 读同一份缓存目录：能读到就证明回写发生了
        assertEquals(900L, assertNotNull(newStore(root = root).read(key)).extractedAt)
    }

    @Test
    fun `takes the newer extractedAt when both sides have the record`() = runTest {
        val key = "conflict"

        // 本地旧（同步关闭时写的）、远端新 → 用远端并回写本地
        val remoteNewer = FakeRemote()
        val olderLocal = freshRoot("conflict-remote-wins")
        newStore(root = olderLocal).write(key, record(extractedAt = 100), null)
        remoteNewer.records[key] =
            json.encodeToString(record(extractedAt = 300).copy(title = "远端版本")).encodeToByteArray()

        val store = newStore(root = olderLocal, remote = remoteNewer)
        assertEquals("远端版本", assertNotNull(store.read(key)).title)
        assertEquals("远端版本", assertNotNull(newStore(root = olderLocal).read(key)).title)

        // 本地新、远端旧 → 用本地，且不去覆盖远端
        val remoteStale = FakeRemote()
        val newerLocal = freshRoot("conflict-local-wins")
        newStore(root = newerLocal).write(key, record(extractedAt = 500).copy(title = "本地版本"), null)
        remoteStale.records[key] =
            json.encodeToString(record(extractedAt = 400).copy(title = "旧远端")).encodeToByteArray()

        val other = newStore(root = newerLocal, remote = remoteStale)
        assertEquals("本地版本", assertNotNull(other.read(key)).title)
        assertTrue(
            remoteStale.records.getValue(key).decodeToString().contains("旧远端"),
            "本地更新时不应把远端覆盖成旧值",
        )
    }

    @Test
    fun `consultRemote controls whether has may hit the network`() = runTest {
        val key = "has-remote"
        val remote = FakeRemote()
        remote.records[key] =
            json.encodeToString(record(fingerprint = "7|\"e\"", complete = true)).encodeToByteArray()
        val store = newStore(root = freshRoot("has-remote"), remote = remote)

        assertFalse(store.has(key, "7|\"e\""), "默认只查本地，避免批量遍历时打出上万次请求")
        assertEquals(0, remote.reads)

        assertTrue(store.has(key, "7|\"e\"", consultRemote = true))
        assertEquals(1, remote.reads)
        assertTrue(store.has(key, "7|\"e\"", consultRemote = true), "第一次查完已回写本地")
        assertEquals(1, remote.reads, "回写之后不应再查远端")
    }

    @Test
    fun `write uploads to the remote and survives a read-only target`() = runTest {
        val key = "read-only"
        val remote = FakeRemote(failWrites = WebDavException.Forbidden("没有访问权限"))
        val store = newStore(root = freshRoot("readonly"), remote = remote)

        store.write(key, record(), byteArrayOf(7))

        assertTrue(remote.records.isEmpty(), "只读目录上不该写成功")
        assertEquals(1, errors.size, "远端写失败必须上报，供卡片提示")
        assertTrue(errors.single() is WebDavException.Forbidden)
        assertNotNull(store.read(key), "远端失败不能影响本地记录")
        assertContentEquals(byteArrayOf(7), store.readCover(key), "封面的本地落盘不受远端失败影响")
    }

    @Test
    fun `uploads only the local records the remote is missing`() = runTest {
        // 先在没有远端的 store 上写本地记录：这就是"同步开关打开之前已经提取过"的真实状态
        val root = freshRoot("upload-missing")
        newStore(root = root).write("keep-local", record(), null)
        newStore(root = root).write("already-there", record(), byteArrayOf(3))

        val remote = FakeRemote()
        remote.records["already-there"] = json.encodeToString(record()).encodeToByteArray()
        val store = newStore(root = root, remote = remote)

        assertEquals(setOf("keep-local", "already-there"), store.localKeys())

        val uploaded = store.localKeys()
            .filterNot(remote.keys()::contains)
            .count { store.upload(it) }

        assertEquals(1, uploaded, "只应补传远端缺的那条")
        assertTrue(remote.records.containsKey("keep-local"))
        assertNull(remote.covers["keep-local"], "没有封面时不应写空封面")
    }

    @Test
    fun `pullFromRemote fills locally missing records and their covers`() = runTest {
        val remote = FakeRemote()
        remote.records["missing"] = json.encodeToString(record()).encodeToByteArray()
        remote.covers["missing"] = byteArrayOf(5, 6)
        remote.records["present"] = json.encodeToString(record()).encodeToByteArray()
        val store = newStore(root = freshRoot("pull-many"), remote = remote)
        store.write("present", record(), null)

        val pulled = store.pullFromRemote(listOf("missing", "present"))

        assertEquals(1, pulled, "本地已有记录的歌曲不该被重复拉取")
        assertNotNull(store.read("missing"))
        assertContentEquals(byteArrayOf(5, 6), store.readCover("missing"))
    }

    @Test
    fun `caches sidecar files per fingerprint and refetches when it changes`() = runTest {
        val key = "sidecar"
        val store = newStore(root = freshRoot("sidecar"))
        var fetches = 0
        // etag 原样带引号：缓存文件名必须能容纳它（Windows 下引号是非法字符）
        val fetch: suspend () -> ByteArray = { fetches++; byteArrayOf(1, 1) }

        val etagA = "\"etag/a\""
        val etagB = "\"etag/b\""
        assertContentEquals(byteArrayOf(1, 1), store.sidecar(key, WebDavSidecarKind.COVER, etagA, fetch))
        assertContentEquals(byteArrayOf(1, 1), store.sidecar(key, WebDavSidecarKind.COVER, etagA, fetch))
        assertEquals(1, fetches, "同一指纹只应拉取一次")

        assertContentEquals(byteArrayOf(1, 1), store.sidecar(key, WebDavSidecarKind.COVER, etagB, fetch))
        assertEquals(2, fetches, "远端换了封面（指纹变化）必须重新拉取")

        // 歌词与封面互不干扰
        store.sidecar(key, WebDavSidecarKind.LYRIC, etagA) { fetches++; byteArrayOf(2) }
        assertEquals(3, fetches)
        store.sidecar(key, WebDavSidecarKind.LYRIC, etagA) { fetches++; byteArrayOf(2) }
        assertEquals(3, fetches, "歌词也应命中缓存")
    }

    @Test
    fun `sidecar returns null when the source file cannot be fetched`() = runTest {
        val store = newStore(root = freshRoot("no-sidecar"))
        var fetches = 0

        val bytes = store.sidecar("no-sidecar", WebDavSidecarKind.LYRIC, "etag") {
            fetches++
            null
        }

        assertNull(bytes)
        assertEquals(1, fetches)
        assertNull(store.sidecar("no-sidecar", WebDavSidecarKind.LYRIC, "etag") { fetches++; null })
        assertEquals(2, fetches, "取不到就不缓存，下次仍要再试")
    }

    /** 内存远端：只实现同步语义所需的部分，读写计数用于断言"是否碰了网络"。 */
    private class FakeRemote(
        private val failWrites: Throwable? = null,
    ) : WebDavMetadataRemote {
        val records = mutableMapOf<String, ByteArray>()
        val covers = mutableMapOf<String, ByteArray>()
        var reads = 0

        fun reset() {
            reads = 0
        }

        override suspend fun ensureReady() {
            failWrites?.let { throw it }
        }

        override suspend fun read(key: String): ByteArray? {
            reads += 1
            return records[key]
        }

        override suspend fun readCover(key: String): ByteArray? = covers[key]

        override suspend fun write(key: String, bytes: ByteArray) {
            failWrites?.let { throw it }
            records[key] = bytes
        }

        override suspend fun writeCover(key: String, bytes: ByteArray) {
            failWrites?.let { throw it }
            covers[key] = bytes
        }

        override suspend fun keys(): Set<String> = records.keys.toSet()
    }
}
