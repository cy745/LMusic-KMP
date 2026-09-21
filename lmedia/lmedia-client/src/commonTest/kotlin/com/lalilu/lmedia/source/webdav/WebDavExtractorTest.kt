package com.lalilu.lmedia.source.webdav

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 提取编排的覆盖测试：窗口触发、只提取一次、按批合并、失败计数与重试空间。
 *
 * 标签读取被替换成确定性实现，因此这里验证的是编排语义；真实标签解析（以及"1 MB 窗口够不够"）
 * 由 [WebDavLiveServerTest] 对着真实服务器与本机音乐文件验证。
 */
class WebDavExtractorTest {

    private val cacheRoot = "build/test-webdav-extractor"
    private val cache = WebDavCache(cacheRoot, Json { ignoreUnknownKeys = true })
    private val store = WebDavMetadataStore(cacheRoot, Json { ignoreUnknownKeys = true })

    private val records = mutableListOf<Pair<String, WebDavMetadataRecord>>()
    private val flushes = mutableListOf<Map<String, WebDavMetadataRecord>>()
    private val coverReads = mutableListOf<Boolean>()

    /** 远大于头部窗口的整曲大小，用于区分"部分提取"与"完整提取"两条路径。 */
    private val totalBytes = 8L * 1024 * 1024

    /** 头部窗口整段写入缓存，用来触发部分提取。 */
    private fun WebDavCache.fillHeadWindow(key: String) {
        appendBytes(key, ByteArray(HEAD_WINDOW_BYTES.toInt()))
    }

    @Test
    fun `extracts at the head window then upgrades to a full record at completion`() = runTest {
        val key = "song-partial"
        store.delete(key)
        cache.delete(key)
        cache.fillHeadWindow(key)

        val extractor = buildExtractor { _, includeCover ->
            coverReads += includeCover
            WebDavExtractedMetadata(
                title = "Friend",
                artist = "HoneyComeBear",
                duration = if (includeCover) 245_000 else 0L,
                cover = if (includeCover) byteArrayOf(1, 2, 3) else null,
            )
        }
        extractor.start(this)

        extractor.request(key)
        extractor.drain()

        assertEquals(listOf(false), coverReads, "头部窗口只读文件头，不读封面")
        assertEquals(1, records.size)
        val partial = assertNotNull(store.read(key))
        assertFalse(partial.complete)
        assertEquals(
            HEAD_WINDOW_BYTES,
            partial.examinedBytes,
            "要记下这次读到多少字节，兜底窗口才不会反复重试",
        )

        // 补满文件后再触发一次：必须升级为完整提取（时长与封面只有完整文件才可靠）
        cache.appendBytes(key, ByteArray((totalBytes - HEAD_WINDOW_BYTES).toInt()))
        assertEquals(totalBytes, cache.filledSize(key), "追加写入必须立刻反映到缓存长度")
        extractor.request(key)
        extractor.drain()

        assertEquals(listOf(false, true), coverReads)
        val record = assertNotNull(store.read(key))
        assertTrue(record.complete)
        assertEquals(245_000L, record.duration)
        assertNotNull(store.readCover(key))
        assertEquals(2, records.size)
        extractor.stop()
    }

    @Test
    fun `drain waits for a just enqueued key instead of returning immediately`() = runTest {
        val key = "song-drain"
        store.delete(key)
        cache.delete(key)
        cache.fillHeadWindow(key)

        val extractor = buildExtractor { _, _ -> WebDavExtractedMetadata(title = "drained") }
        extractor.start(this)

        // 入队后立刻 drain：中间不允许任何挂起点，否则 worker 会"恰好"先跑完，
        // 掩盖 drain 提前返回的问题（曾用 Channel.isEmpty 判断，会整批漏掉收尾合并）
        extractor.request(key)
        extractor.drain()

        assertEquals(1, records.size, "drain 返回时任务必须已经处理完")
        assertEquals(1, flushes.size, "drain 必须完成收尾合并")
        assertEquals("drained", assertNotNull(store.read(key)).title)
        extractor.stop()
    }

    @Test
    fun `flushes a merge batch then flushes the remainder on drain`() = runTest {
        val keys = (1..4).map { "song-batch-$it" }
        keys.forEach { key ->
            store.delete(key)
            cache.delete(key)
            cache.fillHeadWindow(key)
        }

        val extractor = buildExtractor { _, _ ->
            WebDavExtractedMetadata(title = "title")
        }
        extractor.start(this)
        keys.forEach(extractor::request)
        extractor.drain()

        assertEquals(2, flushes.size, "3 条成批 + 结束时收尾")
        assertEquals(3, flushes[0].size)
        assertEquals(1, flushes[1].size)
        assertEquals(4, records.size)
        extractor.stop()
    }

    @Test
    fun `records an empty result for files without tags and never retries them`() = runTest {
        val key = "song-no-tags"
        store.delete(key)
        cache.delete(key)
        cache.appendBytes(key, ByteArray(totalBytes.toInt()))

        val extractor = buildExtractor { _, includeCover ->
            coverReads += includeCover
            null
        }
        extractor.start(this)
        extractor.request(key)
        extractor.drain()

        assertTrue(records.isEmpty(), "没有标签不应产生补丁")
        assertTrue(flushes.isEmpty())
        val record = assertNotNull(store.read(key), "仍要落一条记录，避免每次都重试")
        assertTrue(record.complete)
        assertEquals(0, extractor.progress.value.failed, "没有标签是合法状态，不算失败")

        // 再请求一次：指纹与 complete 都匹配 → 不应再读一次标签
        coverReads.clear()
        extractor.request(key)
        extractor.drain()
        assertTrue(coverReads.isEmpty(), "已完整提取且指纹未变的歌曲不应重复提取")
        extractor.stop()
    }

    @Test
    fun `counts a failure on a complete file and leaves room for a retry`() = runTest {
        val key = "song-failing"
        store.delete(key)
        cache.delete(key)
        cache.appendBytes(key, ByteArray(totalBytes.toInt()))

        val extractor = buildExtractor { _, _ -> error("taglib exploded") }
        extractor.start(this)
        extractor.request(key)
        extractor.drain()

        assertEquals(1, extractor.progress.value.failed)
        assertNull(store.read(key), "失败的完整提取不应写入记录，否则再也没有重试机会")
        assertTrue(records.isEmpty())
        extractor.stop()
    }

    @Test
    fun `does not treat a partial read failure as an error`() = runTest {
        val key = "song-partial-failing"
        store.delete(key)
        cache.delete(key)
        cache.fillHeadWindow(key)

        val extractor = buildExtractor { _, _ -> error("incomplete file") }
        extractor.start(this)
        extractor.request(key)
        extractor.drain()

        assertEquals(0, extractor.progress.value.failed, "半截文件读不出标签是常态")
        assertNull(store.read(key))
        extractor.stop()
    }

    @Test
    fun `grows the window once when the head read produced nothing`() = runTest {
        val key = "song-fallback"
        store.delete(key)
        cache.delete(key)
        cache.fillHeadWindow(key)

        // 头部窗口什么都没读到 → 记一条空的部分记录
        val extractor = buildExtractor { _, includeCover ->
            coverReads += includeCover
            null
        }
        extractor.start(this)
        extractor.request(key)
        extractor.drain()

        assertEquals(listOf(false), coverReads)
        assertEquals(HEAD_WINDOW_BYTES, assertNotNull(store.read(key)).examinedBytes)

        // 缓存涨到兜底窗口：应该再读一次（窗口变大，可能这次就能读到被切断的封面）
        cache.appendBytes(key, ByteArray((HEAD_WINDOW_FALLBACK_BYTES - HEAD_WINDOW_BYTES).toInt()))
        extractor.request(key)
        extractor.drain()
        assertEquals(listOf(false, false), coverReads, "兜底窗口再读一次文件头")

        // 之后继续增长到接近整曲也不该再重试（examinedBytes 已经记在兜底窗口上）
        cache.appendBytes(key, ByteArray(1024 * 1024))
        extractor.request(key)
        extractor.drain()
        assertEquals(2, coverReads.size, "兜底窗口只扩大一次")
        extractor.stop()
    }

    @Test
    fun `skips a track whose complete record already matches`() = runTest {
        val key = "song-ready"
        cache.delete(key)
        cache.fillHeadWindow(key)
        store.write(
            key,
            WebDavMetadataRecord(
                fingerprint = fingerprintOf(key),
                complete = true,
                extractedAt = 1,
                title = "already",
            ),
            null,
        )

        val extractor = buildExtractor { _, _ ->
            coverReads += true
            WebDavExtractedMetadata(title = "should not happen")
        }
        extractor.start(this)
        extractor.request(key)
        extractor.drain()

        assertTrue(coverReads.isEmpty())
        assertEquals(1, extractor.progress.value.extracted, "已有记录的歌曲计入已提取")
        extractor.stop()
    }

    private fun fingerprintOf(key: String): String =
        WebDavMetadataRecord.fingerprintOf(totalBytes, "\"etag-$key\"", null)

    private fun buildExtractor(
        tagAnswer: suspend (String, Boolean) -> WebDavExtractedMetadata?,
    ): WebDavExtractor = WebDavExtractor(
        cache = cache,
        store = store,
        targets = object : WebDavExtractionTargets {
            override suspend fun targetOf(key: String) = WebDavExtractionTarget(
                audio = LAudio(id = key, mediaSourceName = "test"),
                remotePath = "/music/$key.flac",
                totalSize = totalBytes,
                fingerprint = fingerprintOf(key),
            )
        },
        progressState = MutableStateFlow(WebDavExtractionState()),
        onRecord = { target, record -> records += target.audio.id to record },
        onFlush = { batch -> flushes += batch },
        concurrency = 1,
        batchSize = 3,
        tagReaderOverride = tagAnswer,
    )
}
