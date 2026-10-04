package com.lalilu.lmedia.source.webdav

import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.model.albumName
import com.lalilu.lmedia.domain.model.artistName
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaFetchOptions
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.resolvePictureData
import com.lalilu.lmedia.net.NetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        // 走进艺人目录：**音频文件**必须带 getcontentlength / getetag / getcontenttype，
        // 否则后续增量对账无法判断变更（同目录的封面/.lrc 是边车，服务器可能不给内容类型）
        val artistDirectory = directories.first { it.path.contains("HoneyComeBear") }
        val entries = client.propfind(artistDirectory.path, depth = 1).filterNot { it.isDirectory }
        val files = entries.filter { WebDavNamingRules.isAudioFile(it.path) }
        assertTrue(files.isNotEmpty(), "艺人目录下应有音频文件")
        assertTrue(
            files.all { it.contentLength > 0L && it.etag != null && !it.contentType.isNullOrBlank() },
            "音频条目应包含大小、etag 与内容类型：$entries",
        )
        client.close()
    }

    @Test
    fun `scans the real server into a snapshot with derived metadata`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource()

        source.connect(url!!, username, password, root).getOrThrow()
        val snapshot = assertNotNull(awaitSnapshot(source), "扫描未产出快照")

        // 这个库是共享的：后来为模拟器/真机补过音乐，所以只断言固定夹具都在，
        // 不再断言总数——加歌会让总数变化，而那跟本用例要验证的"扫描 + 派生元数据"无关
        val fixtureTitles = listOf(
            "カタオモイワズライ",
            "エンドロール",
            "おじゃま虫",
            "Friend",
            "さよならの支度",
            "オドループ",
            "アンコール",
        )
        val titles = snapshot.audios.map { it.title }
        assertTrue(titles.containsAll(fixtureTitles), "测试库应至少包含 7 首夹具：$titles")
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
        val directory = "/.lmusic/"
        val path = "$directory" + "live-probe.json"
        val payload = """{"probe":"lmusic"}"""

        // 元数据目录由"运行过同步"来提供，可能被谁删掉；这里先确认它可用，
        // 不可用且服务器不支持 MKCOL 时明确跳过——本用例验证的是 PUT/GET 往返，
        // 不该因为共享库里少了个目录就变红
        val hasDirectory = client.propfind(root, depth = 1).any { it.isDirectory && it.path.contains(".lmusic") }
        val usable = hasDirectory || runCatching { client.mkcol(directory) }.getOrDefault(false)
        assumeTrue("服务器上没有 .lmusic 元数据目录且 MKCOL 不可用，跳过", usable)

        client.put(path, payload.encodeToByteArray(), "application/json")
        val readBack = client.download(path)
        client.close()

        assertEquals(payload, readBack?.decodeToString(), "写入的内容应能原样读回")
    }

    @Test
    fun `streams a real track through the loopback proxy byte for byte`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource()
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

    /**
     * 端到端：整首缓存完成后，真实标签、时长与内嵌封面都必须落到本地记录并回灌媒体库。
     *
     * 基准来自同一个文件的**直连字节**（写临时文件后用 taglib 读），因此断言的是"提取管线读到了
     * 真实内容"，而不是"文件名派生出来的东西"。
     */
    @Test
    fun `extracts real tags and cover once the whole track has been cached`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        // 每次跑都用全新的缓存根：否则上一轮留下的完整记录会让提取被合理地跳过
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }
        val remotePath = audio.extra?.get("path").orEmpty()

        // 文件名派生的初始状态：有 title/artist，但没有时长，也没有封面
        assertNull(audio.extra?.get(LAudioExtraKeys.Duration), "首扫不应有时长")

        // 与直连下载的同一文件比对，作为标签的唯一基准
        val reference = readReference(remotePath)
        assertTrue(reference.tags.title?.isNotBlank() == true, "测试素材应有真实标题标签")
        assertTrue(reference.tags.duration > 0L, "测试素材应有真实时长")

        // 先订阅再播放：补丁是 SharedFlow，晚订阅会错过
        val patched = async(Dispatchers.Default) {
            withTimeout(EXTRACTION_TIMEOUT_MILLIS) {
                source.audioPatches.first {
                    it.id == audio.id && it.extra?.get(LAudioExtraKeys.Duration) != null
                }
            }
        }

        // 走代理把整首拉完：30% 触发部分提取，100% 触发完整提取（时长与封面只有这一步才有）
        val media = assertNotNull(source.getMedia(audio), "代理未就绪，无法给出播放地址")
        val proxyUrl = (media as MediaData.Url).url
        val whole = httpGet(proxyUrl, null)
        assertEquals(200, whole.status)

        val patch = patched.await()
        assertEquals(reference.tags.title, patch.title, "补丁应带上真实标题")
        assertEquals(
            reference.tags.duration.toString(),
            patch.extra?.get(LAudioExtraKeys.Duration),
            "补丁应带上真实时长",
        )

        val store = assertNotNull(source.metadataStoreOrNull, "提取存储应已装配")
        val key = WebDavSource.cacheKeyOf(audio.id)
        val record = assertNotNull(store.read(key), "提取记录应落在本地缓存目录")
        assertTrue(record.complete, "整首到齐后记录必须是完整态")
        assertEquals(reference.tags.title, record.title)
        assertEquals(reference.tags.duration, record.duration)

        val cover = assertNotNull(store.readCover(key), "内嵌封面应落盘")
        assertContentEquals(reference.cover, cover, "落盘封面应与文件内嵌封面逐字节一致")

        // 封面能通过数据源接口取到，供上层显示。注意这条路径遵循"同目录边车优先"：
        // HoneyComeBear/ 下有 cover.jpg，所以这里拿到的是边车图；内嵌封面已经在上面对比过落盘内容
        val picture = assertNotNull(source.getPicture(audio, MediaFetchOptions()))
        val pictureBytes = (picture as MediaData.Bytes).bytes
        assertEquals(0xFF, pictureBytes[0].toInt() and 0xFF, "应是真正的 JPEG 数据")
        assertEquals(0xD8, pictureBytes[1].toInt() and 0xFF)

        source.deactivate()
    }

    /**
     * 「封面还在路上」不能被当成「没有封面」：解析器要在预算内等它落盘。
     *
     * 这是「切歌先闪一下底色」的根因——`getPicture` 在这首歌的提取记录落盘前只能返回 null，
     * 而 null 对 Coil 等于**失败**，界面于是在封面到达之前就按失败渲染了（封面其实再等一两秒就有）。
     *
     * 刻意挑一首**没有同目录边车封面**的歌：有边车时 `getPicture` 直接下载边车就返回了，
     * 不经过提取这条路径，测不到"等它落盘"。
     */
    @Test
    fun `a cover that is still coming is waited for instead of reported missing`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val audios = assertNotNull(awaitSnapshot(source)).audios
        val audio = audios.firstOrNull {
            it.title == "エンドロール" && it.extra?.get(WebDavSource.EXTRA_COVER_PATH) == null
        }
        assertNotNull(audio, "夹具里应有不带同目录封面的《エンドロール》：${audios.map { it.title }}")

        val store = assertNotNull(source.metadataStoreOrNull, "提取存储应已装配")
        val key = WebDavSource.cacheKeyOf(audio.id)
        assertNull(store.read(key), "全新缓存根不该有这首的提取记录")

        // ① 没有提取记录 → 数据源必须说"还在路上"，而不是"没有"
        assertTrue(source.isItemContentPending(audio), "没有提取记录时应判定为仍在准备")

        // ② 此刻直接读只能得到 null：这就是修复前上层唯一能拿到的答案
        assertNull(source.getPicture(audio, MediaFetchOptions()), "提取记录还没落盘，此刻读不到封面")

        // ③ 一边等封面出现，一边让播放头那一档字节就位（头部窗口一到，提取才会把封面写进记录）
        val platform = PlatformMediaSource(listOf(source))
        val waiting = async(Dispatchers.Default) {
            platform.resolvePictureData(audio, timeoutMillis = EXTRACTION_TIMEOUT_MILLIS)
        }
        val media = assertNotNull(source.getMedia(audio), "代理未就绪，无法给出播放地址")
        val head = httpGet((media as MediaData.Url).url, range = "bytes=0-${HEAD_WINDOW_BYTES - 1}")
        assertTrue(head.status == 200 || head.status == 206, "取头部窗口失败：${head.status}")

        val picture = assertNotNull(
            waiting.await(),
            "封面还在路上时不该报告没有——那正是界面先闪一下底色的原因",
        )
        val bytes = (picture as MediaData.Bytes).bytes
        assertEquals(0xFF, bytes[0].toInt() and 0xFF, "应是真正的 JPEG 数据")
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)

        // ④ 落盘之后不该再判定为"在路上"
        assertFalse(source.isItemContentPending(audio), "记录已落盘，不该还判定为在路上")
        assertNotNull(store.readCover(key), "封面应已落在本地缓存目录")

        source.deactivate()
    }

    /**
     * 对照：预算为零时**不做等待**，立刻给结论——本地来源、以及"确实没有封面"走的就是这条。
     */
    @Test
    fun `a zero budget answers immediately without waiting`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "エンドロール" }
        assertTrue(source.isItemContentPending(audio))

        val platform = PlatformMediaSource(listOf(source))
        assertNull(
            platform.resolvePictureData(audio),
            "没有预算时不该等待，直接返回 null 让界面走占位",
        )

        source.deactivate()
    }

    /**
     * 缓冲区间上报：开播还没有可显示的东西，整首下完后覆盖到 100%——进度条次级层要的就是这条数据。
     *
     * 信号取自**代理自己的缓存覆盖率**，不是播放器内部缓冲（渐进式 HTTP 下后者常为 0）。
     */
    @Test
    fun `reports buffer progress while the track is being fetched`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val audio = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }

        val media = assertNotNull(source.getMedia(audio), "代理未就绪")
        val proxyUrl = (media as MediaData.Url).url

        // 开播时缓存是空的：没有可显示的区间
        assertEquals(emptyList(), source.bufferProgress(audio.id).first(), "开播时应没有已缓冲区间")

        val whole = httpGet(proxyUrl, range = null)
        assertEquals(200, whole.status)

        val full = withTimeout(EXTRACTION_TIMEOUT_MILLIS) {
            source.bufferProgress(audio.id).first { it.any { range -> range.endFraction >= 1f } }
        }
        assertEquals(1, full.size, "整首下完后应只剩一段覆盖全曲")
        assertEquals(0f, full.single().startFraction, "这一段应当从开头算起")
        assertEquals(1f, full.single().endFraction, "整首下完后覆盖率应为 1")

        source.deactivate()
    }

    /**
     * 头部窗口阈值的实测基准：**只取每首歌开头 1 MB**，标签与内嵌封面都必须能读出来。
     *
     * 这是"固定 1 MB 窗口够不够"这件事的硬证据——阈值调小、或素材换成"元数据在文件尾部"的容器
     * （例如未做 faststart 的 M4A，`moov` 在尾部）都会在这里暴露。实测本机素材的元数据尾部偏移
     * 在 0.18–0.83 MB（中位 0.26 MB）。
     */
    @Test
    fun `the one megabyte head window is enough for tags and cover on every track`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val audios = assertNotNull(awaitSnapshot(source)).audios
        assertTrue(audios.size >= 7, "测试库应有 7 首：${audios.map { it.title }}")

        val client = HttpWebDavClient(config)
        val failures = mutableListOf<String>()
        audios.forEach { audio ->
            val path = audio.extra?.get(WebDavSource.EXTRA_PATH).orEmpty()
            val head = java.io.File.createTempFile("lmusic-head", ".bin").apply { deleteOnExit() }
            try {
                // 只取开头 1 MB：等价于"播放到头部窗口时缓存里有什么"
                head.outputStream().use { out ->
                    client.fetchRange(path, 0L, HEAD_WINDOW_BYTES - 1) { chunk -> out.write(chunk) }
                }
                val title = Taglib.readMetadata(head.absolutePath)?.title
                val cover = runCatching { Taglib.getPicture(head.absolutePath) }.getOrNull()
                if (title.isNullOrBlank()) failures += "${audio.title}: 读不到标题"
                if (cover == null || cover.isEmpty()) failures += "${audio.title}: 读不到封面"
            } catch (throwable: Throwable) {
                failures += "${audio.title}: ${throwable.message}"
            } finally {
                head.delete()
            }
        }
        client.close()
        source.deactivate()

        assertTrue(
            failures.isEmpty(),
            "1 MB 头部窗口应足够读出全部标签与封面，失败项：$failures",
        )
    }

    /**
     * 边车解析：扫描阶段就能从 PROPFIND 结果里认出同目录封面与同名 `.lrc`，因此**不必下载整首**
     * 就能显示封面与歌词——这正是"首扫只做目录遍历"这一取舍的补偿。
     */
    @Test
    fun `resolves the sidecar cover and lyric from the real server`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val source = newSource(cacheRoot = freshCacheRoot())
        source.connect(url!!, username, password, root).getOrThrow()
        val friend = assertNotNull(awaitSnapshot(source)).audios.first { it.title == "Friend" }

        assertEquals("/HoneyComeBear/cover.jpg", friend.extra?.get(WebDavSource.EXTRA_COVER_PATH))
        assertEquals("/HoneyComeBear/02 Friend.lrc", friend.extra?.get(WebDavSource.EXTRA_LYRIC_PATH))
        assertNotNull(friend.extra?.get(WebDavSource.EXTRA_COVER_FINGERPRINT))

        val picture = assertNotNull(source.getPicture(friend, MediaFetchOptions()), "应取到同目录封面")
        val coverBytes = (picture as MediaData.Bytes).bytes
        assertEquals(0xFF, coverBytes[0].toInt() and 0xFF, "应是真正的 JPEG 数据")
        assertEquals(0xD8, coverBytes[1].toInt() and 0xFF)
        assertTrue(coverBytes.size > 10_000, "封面应是服务器上的整张图：${coverBytes.size} 字节")

        val lyric = assertNotNull(source.getLyric(friend), "应取到同名 .lrc")
        assertTrue(lyric.contains("first line") && lyric.contains("second line"), lyric)

        // 二次读取命中本地缓存，内容必须一致
        assertEquals(lyric, source.getLyric(friend))
        assertContentEquals(coverBytes, (assertNotNull(source.getPicture(friend, MediaFetchOptions())) as MediaData.Bytes).bytes)
        source.deactivate()
    }

    /**
     * 元数据同步走真实 PUT / GET：打开开关后写入的记录会落到服务器，全新缓存（等价"新设备"）
     * 打开开关后能把这条记录取回本地。
     */
    @Test
    fun `syncs metadata to the real server and back into a fresh cache`() = runTest {
        assumeTrue("未配置 LMUSIC_WEBDAV_URL，跳过真实服务用例", url != null)
        val title = "Live 同步 ${kotlin.random.Random.nextLong()}"

        // 写入端：开关先打开，写入即上传（这条路径不依赖"远端是否已有该键"）
        val writer = newSource(cacheRoot = freshCacheRoot())
        writer.connect(url!!, username, password, root).getOrThrow()
        val audio = assertNotNull(awaitSnapshot(writer)).audios.first { it.title == "Friend" }
        val key = WebDavSource.cacheKeyOf(audio.id)
        val coverBytes = byteArrayOf(9, 8, 7, 6, 5)

        writer.updateOptions(metaSyncEnabled = true).getOrThrow()
        assertTrue(awaitSyncSettled(writer) is WebDavMetadataSyncState.Synced)
        assertNotNull(writer.metadataStoreOrNull).write(
            key,
            WebDavMetadataRecord(
                fingerprint = "live-sync",
                complete = true,
                extractedAt = 1,
                title = title,
                hasCover = true,
            ),
            coverBytes,
        )

        // 服务器上必须真的有这两份文件
        val direct = HttpWebDavClient(config)
        val remoteJson = assertNotNull(
            direct.download("/.lmusic/$key.json"),
            "记录应上传到默认的同步目录",
        )
        assertTrue(remoteJson.decodeToString().contains(title), remoteJson.decodeToString())
        assertContentEquals(
            coverBytes,
            assertNotNull(direct.download("/.lmusic/$key.cover"), "封面应一起上传"),
        )
        direct.close()

        // 读取端：全新缓存目录（等价"新设备"）→ 同步后本地能看到服务器上那条记录。
        // 注意 KV 是进程级共享的（KVContext.kvMap 按 prefix+key 复用同一个 KVItem），
        // 因此这里不复用"开关初始为关"这类状态假设，只对结果下断言。
        val readerRoot = freshCacheRoot()
        val reader = newSource(cacheRoot = readerRoot)
        reader.connect(url!!, username, password, root).getOrThrow()
        assertNotNull(awaitSnapshot(reader))

        reader.updateOptions(metaSyncEnabled = true).getOrThrow()
        val readerState = awaitSyncSettled(reader)
        assertTrue(readerState !is WebDavMetadataSyncState.Failed, "同步不应失败，实际 $readerState")

        // 换一个没有远端层的 store 读同一份缓存目录：能读到才说明真的回写本地了
        val offline = WebDavMetadataStore(readerRoot, Json { ignoreUnknownKeys = true })
        val pulled = assertNotNull(offline.read(key), "记录应从服务器同步到本地")
        assertEquals(title, pulled.title)
        assertContentEquals(coverBytes, offline.readCover(key))

        writer.deactivate()
        reader.deactivate()
    }

    /**
     * 真实服务用例统一用"Wi-Fi"网络观测：门控本身由 `WebDavSourceTest` 覆盖，
     * 这里不该让"当前桌面网卡叫什么名字"决定用例成败。
     */
    private fun newSource(cacheRoot: String = System.getProperty("java.io.tmpdir")) = WebDavSource(
        clientFactory = { HttpWebDavClientFactory().create(it) },
        cacheRootProvider = { cacheRoot },
        json = Json { ignoreUnknownKeys = true },
        kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
        networkObservation = { flowOf(NetworkType.WIFI) },
    )

    private fun freshCacheRoot(): String =
        java.nio.file.Files.createTempDirectory("lmusic-webdav-extract")
            .toFile()
            .apply { deleteOnExit() }
            .absolutePath

    /** 直连下载同一首歌并按 taglib 读取，作为标签与封面的基准。 */
    private suspend fun readReference(remotePath: String): Reference {
        val client = HttpWebDavClient(config)
        val bytes = assertNotNull(client.download(remotePath), "直连下载失败：$remotePath")
        client.close()

        val file = java.io.File.createTempFile("lmusic-webdav-ref", ".flac").apply {
            deleteOnExit()
            writeBytes(bytes)
        }
        val tags = assertNotNull(Taglib.readMetadata(file.absolutePath), "taglib 未能读取基准标签")
        val cover = assertNotNull(Taglib.getPicture(file.absolutePath), "taglib 未能读取基准封面")
        return Reference(tags = tags, cover = cover)
    }

    private class Reference(val tags: Metadata, val cover: ByteArray)

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

    private suspend fun awaitSyncSettled(
        source: WebDavSource,
        timeoutMillis: Long = 60_000,
    ): WebDavMetadataSyncState = withContext(Dispatchers.Default) {
        withTimeout(timeoutMillis) {
            source.metadataSyncState.first {
                it is WebDavMetadataSyncState.Synced || it is WebDavMetadataSyncState.Failed
            }
        }
    }

    private companion object {
        /** 30MB 级文件在本地容器上几秒内下完，留足余量给标签与封面解析。 */
        const val EXTRACTION_TIMEOUT_MILLIS = 120_000L

        /** 头部窗口：提取标签与内嵌封面只需要开头这一档字节（见下面那条实测基准用例）。 */
        const val HEAD_WINDOW_BYTES = 1024L * 1024L
    }
}
