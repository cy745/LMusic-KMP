package com.lalilu.lmedia.source.subsonic

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.ext.retrieveAllPage
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmedia.source.Configurable
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.source.subsonic.entity.toLrcContent
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class SubsonicSource(
    private val json: Json,
    private val saver: com.lalilu.lmedia.source.Saver
) : MediaSource, MediaDataSource, Configurable, CoroutineScope {

    companion object {
        private const val TAG = "SubsonicSource"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Logger.e(tag = TAG, throwable = throwable, messageString = "${throwable.message}")
        }

    override val name: String = TAG
    override val dataSource: MediaDataSource = this
    private val logger = Logger.withTag(name)

    private var client: HttpClient? = null
    private var subsonicApi: SubsonicApi? = null
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    private var loadingJob: Job? = null

    override val config: MediaSourceConfig = buildConfig(
        onConfigChange = ::onConfigChange,
        key = name,
        name = "Subsonic/Navidrome",
        description = "连接 Subsonic API 或 Navidrome 服务器",
        saver = saver
    ) {
        // 用户可见的属性
        property<String>("url")
            .provide("http://192.168.3.6:4533/rest/")
        property<String>("username")
            .provide("qiu745")
        property<String>("password", mutable = true)
            .provide("")

        // 用户不可见的属性（计算得到）
        property<String>("salt", mutable = true, visibleInUI = false)
            .provide("")
        property<String>("token", mutable = true, visibleInUI = false)
            .provide("")
        property<String>("client", mutable = false, visibleInUI = false)
            .provide("LMusic")
        property<String>("version", mutable = false, visibleInUI = false)
            .provide("6.1.4")
        property<String>("format", mutable = false, visibleInUI = false)
            .provide("json")

        // 连接功能
        function<Unit>(
            key = "Connect",
            description = "连接 Subsonic 服务器",
            isAvailable = { state.value is SnapshotState.Idle }
        ).onCall {
            connect()
        }

        // 取消功能
        function<Unit>(
            key = "Cancel",
            description = "取消当前执行任务",
            isAvailable = {
                state.value is SnapshotState.Loading
            }
        ).onCall {
            loadingJob?.cancel()
            logger.i(messageString = "Cancel requested")
        }

        // 重置功能
        function<Unit>(
            key = "Reset",
            description = "重置 Subsonic 连接",
            isAvailable = {
                state.value !is SnapshotState.Loading && state.value !is SnapshotState.Idle
            }
        ).onCall {
            reset()
        }

        // 刷新功能
        function<Unit>(
            key = "Refresh",
            description = "刷新媒体库",
            isAvailable = {
                state.value !is SnapshotState.Loading && state.value !is SnapshotState.Idle
            }
        ).onCall {
            loadData()
        }
    }

    override fun onConfigChange() {

    }

    override fun init() {
        connect(isInitialize = true)
    }

    private fun connect(isInitialize: Boolean = false) {
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin(if (isInitialize) "Restoring connection..." else "Connecting...")
            client?.close()
            client = null
            subsonicApi = null

            try {
                if (isInitialize) {
                    // 初始化连接时，只校验salt和token
                    val salt = config.get<String>("salt").getOrThrow()
                    val token = config.get<String>("token").getOrThrow()

                    if (salt.isEmpty() || token.isEmpty()) {
                        throw IllegalArgumentException("配置参数错误")
                    }
                } else {
                    // 1. 获取配置，并进行校验
                    val url = config.get<String>("url").getOrThrow()
                    val username = config.get<String>("username").getOrThrow()
                    val password = config.get<String>("password").getOrNull() ?: ""

                    if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                        throw IllegalArgumentException("请填写完整的配置")
                    }

                    // 2. 生成 salt 和 token
                    val salt = generateSalt()
                    val token = generateToken(password, salt)

                    // 3. 更新配置（清除密码，保存 salt 和 token）
                    config.update { setter ->
                        setter("password", "")  // 清除密码
                        setter("salt", salt)    // 保存 salt
                        setter("token", token)  // 保存 token
                    }
                }

                // 4. 创建客户端和 API
                recreateClient()

                // 5. 测试连接
                testConnection()

                // 连接成功后直接在同一任务内发布完整结果。
                val api = subsonicApi ?: error("API not initialized")
                stateStore.succeed(taskId, getSongs(api))
            } catch (cancelled: CancellationException) {
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                logger.e(messageString = "连接失败: ${throwable.message}", throwable = throwable)
                if (isInitialize) {
                    stateStore.reset()
                } else {
                    stateStore.fail(taskId, "[$name]${throwable.message}")
                }
            }
        }
    }

    private fun reset() {
        client?.close()
        client = null
        subsonicApi = null
        loadingJob?.cancel()
        loadingJob = launch { stateStore.reset() }

        // 重置配置（保留 url 和 username，清除认证信息）
        config.update { setter ->
            setter("password", "")
            setter("salt", "")
            setter("token", "")
        }
    }

    private fun loadData() {
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin()
            try {
                val api = subsonicApi ?: throw IllegalStateException("Not connected")
                stateStore.succeed(taskId, getSongs(api))
            } catch (cancelled: CancellationException) {
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                logger.e(messageString = "加载数据失败: ${throwable.message}", throwable = throwable)
                stateStore.fail(taskId, "[$name]${throwable.message}")
            }
        }
    }

    private fun generateSalt(): String {
        // 生成16字节的随机 salt
        return Random.nextBytes(16).toHexString()
    }

    private fun generateToken(password: String, salt: String): String {
        // Subsonic 认证方式：md5(password + salt)
        return (password + salt).md5()
    }

    private fun recreateClient() {
        client?.close()

        val url = config.get<String>("url").getOrThrow()
        val username = config.get<String>("username").getOrThrow()
        val salt = config.get<String>("salt").getOrThrow()
        val token = config.get<String>("token").getOrThrow()
        val clientName = config.get<String>("client").getOrThrow()
        val version = config.get<String>("version").getOrThrow()
        val format = config.get<String>("format").getOrThrow()

        // 创建 Subsonic 认证插件
        val subsonicPlugin = createClientPlugin("SUBSONIC_PAYLOAD_PLUGIN") {
            onRequest { request, _ ->
                request.parameter("u", username)
                request.parameter("v", version)
                request.parameter("c", clientName)
                request.parameter("f", format)
                request.parameter("s", salt)
                request.parameter("t", token)
            }
        }

        client = HttpClient {
            install(ContentNegotiation) { json(json) }
            install(subsonicPlugin)
        }

        val ktorfit = ktorfit {
            httpClient(client!!)
            baseUrl(url)
        }

        subsonicApi = ktorfit.createSubsonicApi()
    }

    private suspend fun testConnection() {
        val api = subsonicApi ?: throw IllegalStateException("API not initialized")

        val pingResp = runCatching { api.ping().response }
            .getOrElse { throw Exception("Ping failed: ${it.message}") }

        if (pingResp.isError) {
            throw Exception("Ping error: ${pingResp.error?.message}")
        }
    }

    suspend fun CoroutineScope.getSongs(api: SubsonicApi): List<LAudio> {
        // 遍历获取所有专辑
        val albums = retrieveAllPage { size, offset ->
            api.getAlbumList2(
                type = "newest",
                size = size,
                offset = offset
            ).response.albumList2.album
        }

        // 保留专辑上下文，以便把源端 albumId / artistId 写入每首歌的 extra。
        val albumDetails = albums.map { album ->
            async { api.getAlbum(album.id).response.album }
        }.awaitAll()

        return albumDetails.flatMap { album ->
            album.song.map { song ->
                val artist = song.artist.takeIf(String::isNotBlank) ?: "Unknown"
                LAudio(
                    id = "${LAudio.ID_PREFIX}${song.id}",
                    title = song.title.takeIf(String::isNotBlank) ?: "Unknown",
                    subtitle = artist,
                    mediaSourceName = name,
                    extra = buildMap {
                        put("sourceId", song.id)
                        song.coverArt.takeIf(String::isNotBlank)?.let { put("coverArt", it) }
                        song.size.takeIf { it > 0L }?.let { put("file_size", it.toString()) }
                        song.contentType.takeIf(String::isNotBlank)?.let { put("content_type", it) }
                        song.suffix.takeIf(String::isNotBlank)?.let { put("suffix", it) }
                        song.bitRate.takeIf { it > 0 }?.let { put("bitRate", it.toString()) }
                        song.path.takeIf(String::isNotBlank)?.let { put("path", it) }
                        album.artistId.takeIf(String::isNotBlank)
                            ?.let { put(LAudioExtraKeys.ArtistId, it) }
                        put(LAudioExtraKeys.ArtistName, artist)
                        album.id.takeIf(String::isNotBlank)
                            ?.let { put(LAudioExtraKeys.AlbumId, it) }
                        song.album.takeIf(String::isNotBlank)
                            ?.let { put(LAudioExtraKeys.AlbumName, it) }
                        album.artist.takeIf(String::isNotBlank)
                            ?.let { put(LAudioExtraKeys.AlbumArtist, it) }
                        song.genre.takeIf(String::isNotBlank)
                            ?.let { put(LAudioExtraKeys.Genre, it) }
                        (song.duration * 1000L).takeIf { it > 0L }
                            ?.let { put(LAudioExtraKeys.Duration, it.toString()) }
                        song.year.takeIf { it > 0 }
                            ?.let { put(LAudioExtraKeys.Date, it.toString()) }
                        song.track.takeIf { it > 0 }
                            ?.let { put(LAudioExtraKeys.Track, it.toString()) }
                    },
                )
            }
        }
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.io) {
        val api = subsonicApi ?: return@withContext null
        val sourceId = song.extra?.get("sourceId") ?: return@withContext null
        val lyric = api.getLyricsBySongId(sourceId)

        lyric.response.lyricsList.structuredLyrics.firstOrNull()
            ?.toLrcContent()
            ?.takeIf { it.isNotEmpty() }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val sourceId = song.extra?.get("sourceId") ?: return null
        val url = buildSubsonicUrl("stream", mapOf("id" to sourceId))
        return MediaData.Url(url)
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val coverArtId = song.extra?.get("coverArt")
            ?: song.extra?.get("sourceId")
            ?: return null
        val url = buildSubsonicUrl("getCoverArt", mapOf("id" to coverArtId))
        return MediaData.Url(url)
    }

    private fun buildSubsonicUrl(path: String, extras: Map<String, String>): String {
        val extraStr = extras.toList().joinToString(separator = "") { "&${it.first}=${it.second}" }

        val url = config.get<String>("url").getOrNull() ?: ""
        val username = config.get<String>("username").getOrNull() ?: ""
        val token = config.get<String>("token").getOrNull() ?: ""
        val salt = config.get<String>("salt").getOrNull() ?: ""
        val version = config.get<String>("version").getOrNull() ?: ""
        val client = config.get<String>("client").getOrNull() ?: ""
        val format = config.get<String>("format").getOrNull() ?: ""

        return "${url}$path" +
                "?u=$username" +
                "&t=$token" +
                "&s=$salt" +
                "&v=$version" +
                "&c=$client" +
                "&f=$format" +
                extraStr
    }
}
