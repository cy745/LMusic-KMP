package com.lalilu.lmedia.source.subsonic

import com.lalilu.common.kv.testing.InMemoryKVSaver
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.source.subsonic.entity.GetAlbumList2Response
import com.lalilu.lmedia.source.subsonic.entity.GetAlbumResponse
import com.lalilu.lmedia.source.subsonic.entity.GetArtistInfo2Response
import com.lalilu.lmedia.source.subsonic.entity.GetArtistResponse
import com.lalilu.lmedia.source.subsonic.entity.GetArtistsResponse
import com.lalilu.lmedia.source.subsonic.entity.GetLyricByIdResponse
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Subsonic 源同步链路的覆盖测试。
 * 背景：媒体数据源重构后 Subsonic 一直缺少测试，导致"单个专辑请求失败（非 JSON 响应）
 * 会拖垮整个同步"的回归未被发现——对应错误为
 * ktor NoTransformationFoundException: "Expected response body of the type ..."。
 */
class SubsonicSourceSyncTest {

    @BeforeTest
    fun setup() {
        startKoin {
            modules(module { single { Json { ignoreUnknownKeys = true } } })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private val source by lazy {
        SubsonicSource(
            json = Json { ignoreUnknownKeys = true },
            kv = LMediaKV(InMemoryKVSaver(mutableMapOf())),
        )
    }

    private fun album(
        id: String,
        name: String,
        artistId: String,
        artist: String,
        songs: List<GetAlbumResponse.Song>,
    ) = GetAlbumList2Response.Album(
        id = id,
        name = name,
        artistId = artistId,
        artist = artist,
    )

    private fun song(id: String, title: String) = GetAlbumResponse.Song(
        id = id,
        parent = "alb-1",
        title = title,
        album = "Album 1",
        artist = "Artist",
        track = 1,
        year = 2024,
        genre = "J-Pop",
        coverArt = "co-$id",
        size = 1_024L,
        contentType = "audio/flac",
        suffix = "flac",
        duration = 123,
        bitRate = 320,
        path = "Artist/Album/$id.flac",
    )

    private fun apiWith(
        albumList: List<GetAlbumList2Response.Album>,
        albumResults: (String) -> GetAlbumResponse,
    ) = object : SubsonicApi {
        override suspend fun ping() = error("not used in this test")
        override suspend fun getArtists(musicFolderId: String?) = error("not used in this test")
        override suspend fun getArtist(id: String) = error("not used in this test")
        override suspend fun getArtistInfo2(id: String) = error("not used in this test")
        override suspend fun getAlbumList2(
            type: String, size: Int?, offset: Int?, fromYear: Int?, toYear: Int?,
            genre: String?, musicFolderId: String?,
        ) = SubsonicResponseWrapper(
            GetAlbumList2Response(albumList2 = GetAlbumList2Response.AlbumList2(album = albumList))
        )

        override suspend fun getAlbumInfo2(id: String) = error("not used in this test")
        override suspend fun getAlbum(id: String) = SubsonicResponseWrapper(albumResults(id))
        override suspend fun getLyricsBySongId(id: String) = error("not used in this test")
    }

    private fun albumResponseFor(albumId: String, vararg songs: GetAlbumResponse.Song) =
        GetAlbumResponse(album = GetAlbumResponse.Album(
            id = albumId,
            name = "Album $albumId",
            artist = "Artist",
            artistId = "art-$albumId",
            song = songs.toList(),
        ))

    @Test
    fun `sync maps songs into LAudio with source extras`() = runBlocking {
        val api = apiWith(
            albumList = listOf(album("alb-1", "Album 1", "art-1", "Artist", emptyList())),
            albumResults = {
                albumResponseFor(
                    "alb-1",
                    song("song-1", "Title 1"),
                    song("song-2", "Title 2"),
                )
            },
        )

        val audios = with(source) { getSongs(api) }

        assertEquals(2, audios.size)
        val first = audios.first()
        assertEquals("${LAudio.ID_PREFIX}song-1", first.id)
        assertEquals("Title 1", first.title)
        assertEquals("Artist", first.subtitle)
        assertEquals(source.name, first.mediaSourceName)

        val extra = first.extra.orEmpty()
        assertEquals("song-1", extra["sourceId"])
        assertEquals("co-song-1", extra["coverArt"])
        assertEquals("art-alb-1", extra[LAudioExtraKeys.ArtistId])
        assertEquals("Artist", extra[LAudioExtraKeys.ArtistName])
        assertEquals("alb-1", extra[LAudioExtraKeys.AlbumId])
        assertEquals("Album 1", extra[LAudioExtraKeys.AlbumName])
        assertEquals("Artist", extra[LAudioExtraKeys.AlbumArtist])
        assertEquals("J-Pop", extra[LAudioExtraKeys.Genre])
        assertEquals("123000", extra[LAudioExtraKeys.Duration])
        assertEquals("2024", extra[LAudioExtraKeys.Date])
        assertEquals("1", extra[LAudioExtraKeys.Track])
        assertEquals("1024", extra["file_size"])
        assertEquals("audio/flac", extra["content_type"])
        assertEquals("flac", extra["suffix"])
        assertEquals("320", extra["bitRate"])
        assertEquals("Artist/Album/song-1.flac", extra["path"])
    }

    @Test
    fun `sync skips albums whose detail request fails`() = runBlocking {
        val api = apiWith(
            albumList = listOf(
                album("alb-1", "Album 1", "art-1", "Artist", emptyList()),
                album("alb-2", "Album 2", "art-2", "Artist", emptyList()),
                album("alb-3", "Album 3", "art-3", "Artist", emptyList()),
            ),
            albumResults = { id ->
                if (id == "alb-2") throw IllegalStateException("boom: gateway 502")
                albumResponseFor(id, song("s-$id", "Title $id"))
            },
        )

        val audios = with(source) { getSongs(api) }

        // 失败的单个专辑被跳过，其余专辑正常入库，同步不整体失败。
        assertEquals(2, audios.size)
        assertEquals(setOf("s-alb-1", "s-alb-3"), audios.map { it.extra?.get("sourceId") }.toSet())
    }

    @Test
    fun `sync tolerates album responses in failed status`() = runBlocking {
        val api = apiWith(
            albumList = listOf(
                album("alb-1", "Album 1", "art-1", "Artist", emptyList()),
                album("alb-2", "Album 2", "art-2", "Artist", emptyList()),
            ),
            albumResults = { id ->
                if (id == "alb-2") {
                    Json { ignoreUnknownKeys = true }.decodeFromString(
                        """{"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome","serverVersion":"0.58.0","openSubsonic":true,"error":{"code":70,"message":"Album not found"}}}"""
                    )
                } else {
                    albumResponseFor(id, song("s-$id", "Title $id"))
                }
            },
        )

        val audios = with(source) { getSongs(api) }

        assertEquals(1, audios.size)
        assertEquals("s-alb-1", audios.single().extra?.get("sourceId"))
    }

    @Test
    fun `sync fails loudly when every album request fails`() {
        val api = apiWith(
            albumList = listOf(
                album("alb-1", "Album 1", "art-1", "Artist", emptyList()),
                album("alb-2", "Album 2", "art-2", "Artist", emptyList()),
            ),
            albumResults = { throw IllegalStateException("gateway 502") },
        )

        assertFailsWith<IllegalStateException> {
            with(source) { runBlocking { getSongs(api) } }
        }.also { assertTrue("所有专辑请求均失败" in (it.message ?: "")) }
    }

    @Test
    fun `sync returns empty list for empty album list`() = runBlocking {
        val api = apiWith(albumList = emptyList(), albumResults = { error("should not be called") })
        assertEquals(emptyList(), with(source) { getSongs(api) })
    }

    /** 用 MockEngine 构造与产品一致的 ktorfit API，记录每次请求的路径。 */
    private fun mockSubsonicApi(normalizedBaseUrl: String): Pair<SubsonicApi, () -> List<String>> {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(
                content = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.58.0","openSubsonic":true}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = ktorfit { httpClient(client); baseUrl(normalizedBaseUrl) }.createSubsonicApi()
        return api to { paths.toList() }
    }

    @Test
    fun `api url normalization appends rest to root paths`() {
        // 用户只填主机/端口（缺 /rest/）时自动补齐标准 API 根
        assertEquals("http://192.168.3.6:4533/rest/", SubsonicSource.normalizeApiUrl("http://192.168.3.6:4533"))
        assertEquals("http://192.168.3.6:4533/rest/", SubsonicSource.normalizeApiUrl("http://192.168.3.6:4533/"))
        assertEquals("http://192.168.3.6:4533/rest/", SubsonicSource.normalizeApiUrl("192.168.3.6:4533"))
        assertEquals("http://host:8080/rest/", SubsonicSource.normalizeApiUrl(" http://host:8080 "))

        // 无端口、无协议、IP/域名、IPv6、带凭据、多斜杠等根路径形态
        assertEquals("http://192.168.3.6/rest/", SubsonicSource.normalizeApiUrl("192.168.3.6"))
        assertEquals("http://music.example.com/rest/", SubsonicSource.normalizeApiUrl("music.example.com"))
        assertEquals("http://[::1]:4533/rest/", SubsonicSource.normalizeApiUrl("http://[::1]:4533"))
        assertEquals("http://user:pass@host:8080/rest/", SubsonicSource.normalizeApiUrl("http://user:pass@host:8080"))
        assertEquals("http://host:8080/rest/", SubsonicSource.normalizeApiUrl("http://host:8080////"))
    }

    @Test
    fun `api url normalization keeps explicit paths unchanged`() {
        // 已带路径的地址保持原样（不做猜测，兼容反代/自定义路径）
        assertEquals("https://music.example.com/rest/", SubsonicSource.normalizeApiUrl("https://music.example.com/rest"))
        assertEquals("https://music.example.com/rest/", SubsonicSource.normalizeApiUrl("https://music.example.com/rest/"))
        assertEquals("http://host:8080/api/", SubsonicSource.normalizeApiUrl("http://host:8080/api"))
        assertEquals("http://host:8080/api/", SubsonicSource.normalizeApiUrl("http://host:8080/api/"))
        assertEquals("http://host:8080/sub/music/", SubsonicSource.normalizeApiUrl("http://host:8080/sub/music"))
    }

    @Test
    fun `api url normalization is safe for blank and malformed inputs`() {
        assertEquals("", SubsonicSource.normalizeApiUrl(""))
        assertEquals("", SubsonicSource.normalizeApiUrl("   "))
        // 非法输入不抛异常：原样返回（带基础修正），由请求层给出可读错误
        val malformed = SubsonicSource.normalizeApiUrl("://bad input")
        assertTrue(malformed.isNotEmpty())
        assertEquals("http://host:8080/rest/", SubsonicSource.normalizeApiUrl("http://host:8080"))
    }

    @Test
    fun `requests hit the correct endpoint after url normalization`() = runBlocking {
        // 用户只填根地址（真机复现场景）→ 实际请求 /rest/ping
        val (api1, paths1) = mockSubsonicApi(SubsonicSource.normalizeApiUrl("http://192.168.3.6:4533"))
        api1.ping()
        assertEquals("/rest/ping", paths1().single())

        // 无协议输入 → 同样修正
        val (api2, paths2) = mockSubsonicApi(SubsonicSource.normalizeApiUrl("192.168.3.6:4533"))
        api2.ping()
        assertEquals("/rest/ping", paths2().single())

        // 已带 /rest/ → 原样命中
        val (api3, paths3) = mockSubsonicApi(SubsonicSource.normalizeApiUrl("http://192.168.3.6:4533/rest/"))
        api3.ping()
        assertEquals("/rest/ping", paths3().single())

        // 自定义路径 → 尊重输入（不求 /rest/）
        val (api4, paths4) = mockSubsonicApi(SubsonicSource.normalizeApiUrl("http://host:8080/api"))
        api4.ping()
        assertEquals("/api/ping", paths4().single())
    }
}
