package com.lalilu.lmedia.source.subsonic

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.ext.md5
import com.lalilu.common.ext.retrieveAllPage
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.*
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
    kv: LMediaKV,
) : MediaSource, MediaDataSource, CoroutineScope {

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
    override val contentState = stateStore.contentState
    private var loadingJob: Job? = null

    val config: KVItem<SubsonicConfig> = kv.obtain(
        key = "${name}Config",
        defaultValue = SubsonicConfig.Empty,
    ).apply { disableAutoSave() }
    private var activeConfig: SubsonicConfig = config.value

    override fun init() {
        if (activeConfig.isConfigured) {
            connectStored(isInitialize = true)
        } else {
            stateStore.content.unavailable("Server not configured")
        }
    }

    fun connect(url: String, username: String, password: String): Result<Unit> = runCatching {
        val current = activeConfig
        val trimmedUrl = url.trim()
        require(trimmedUrl.isNotBlank() && username.isNotBlank()) { "请填写完整的服务器地址和用户名" }
        val normalizedUrl = if (trimmedUrl.endsWith('/')) trimmedUrl else "$trimmedUrl/"

        val canReuseAuthentication = password.isBlank() &&
            normalizedUrl == current.url &&
            username.trim() == current.username &&
            current.salt.isNotBlank() &&
            current.token.isNotBlank()
        require(password.isNotBlank() || canReuseAuthentication) { "请输入密码" }

        config.value = if (canReuseAuthentication) {
            current.copy(url = normalizedUrl, username = username.trim())
        } else {
            val salt = generateSalt()
            current.copy(
                url = normalizedUrl,
                username = username.trim(),
                salt = salt,
                token = generateToken(password, salt),
            )
        }
        config.save()
        activeConfig = config.value
        connectStored()
    }

    fun retry(): Result<Unit> = runCatching {
        require(activeConfig.isConfigured) { "请先填写连接配置" }
        connectStored()
    }

    private fun connectStored(isInitialize: Boolean = false) {
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin(if (isInitialize) "Restoring connection..." else "Connecting...")
            stateStore.content.preparing(preserveReady = false)
            client?.close()
            client = null
            subsonicApi = null

            try {
                require(activeConfig.isConfigured) { "配置参数错误" }

                recreateClient(activeConfig)

                testConnection()

                // 连接成功后直接在同一任务内发布完整结果。
                val api = subsonicApi ?: error("API not initialized")
                if (stateStore.succeed(taskId, getSongs(api)) != null) {
                    stateStore.content.ready()
                }
            } catch (cancelled: CancellationException) {
                if (stateStore.cancel(taskId)) {
                    stateStore.content.unavailable("Cancelled", preserveReady = true)
                }
                throw cancelled
            } catch (throwable: Throwable) {
                logger.e(messageString = "连接失败: ${throwable.message}", throwable = throwable)
                if (isInitialize) {
                    if (stateStore.fail(taskId, throwable.message ?: "Connection failed")) {
                        stateStore.reset()
                        stateStore.content.unavailable(throwable.message ?: "Connection failed")
                    }
                } else {
                    if (stateStore.fail(taskId, "[$name]${throwable.message}")) {
                        stateStore.content.unavailable(throwable.message ?: "Connection failed")
                    }
                }
            }
        }
    }

    fun cancel() {
        loadingJob?.cancel()
        stateStore.content.unavailable("Cancelled", preserveReady = true)
        logger.i(messageString = "Cancel requested")
    }

    fun reset() {
        client?.close()
        client = null
        subsonicApi = null
        loadingJob?.cancel()
        stateStore.content.unavailable("Authentication cleared")
        loadingJob = launch { stateStore.reset() }

        // 重置配置（保留 url 和 username，清除认证信息）
        config.value = activeConfig.copy(salt = "", token = "")
        config.save()
        activeConfig = config.value
    }

    fun refresh(): Result<Unit> = runCatching {
        require(activeConfig.isConfigured) { "请先填写连接配置" }
        loadingJob?.cancel()
        loadingJob = launch {
            val taskId = stateStore.begin()
            stateStore.content.preparing()
            try {
                val api = subsonicApi ?: throw IllegalStateException("Not connected")
                if (stateStore.succeed(taskId, getSongs(api)) != null) {
                    stateStore.content.ready()
                }
            } catch (cancelled: CancellationException) {
                if (stateStore.cancel(taskId)) {
                    stateStore.content.unavailable("Cancelled", preserveReady = true)
                }
                throw cancelled
            } catch (throwable: Throwable) {
                logger.e(messageString = "加载数据失败: ${throwable.message}", throwable = throwable)
                if (stateStore.fail(taskId, "[$name]${throwable.message}")) {
                    stateStore.content.unavailable(
                        throwable.message ?: "Loading failed",
                        preserveReady = true,
                    )
                }
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

    private fun recreateClient(config: SubsonicConfig) {
        client?.close()

        val url = config.url
        val username = config.username
        val salt = config.salt
        val token = config.token
        val clientName = config.client
        val version = config.version
        val format = config.format

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

        val config = activeConfig
        val url = config.url
        val username = config.username
        val token = config.token
        val salt = config.salt
        val version = config.version
        val client = config.client
        val format = config.format

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
