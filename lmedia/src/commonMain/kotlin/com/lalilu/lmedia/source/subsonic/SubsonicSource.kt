package com.lalilu.lmedia.source.subsonic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.retrieveAllPage
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.source.MediaDataSource
import com.lalilu.lmedia.source.MediaSource
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class SubsonicSource(
    lMediaKV: LMediaKV,
    json: Json
) : MediaSource, MediaDataSource {
    override val name: String = "SubsonicSource"
    private val logger = Logger.withTag(name)

    /**
     * 客户端配置参数
     */
    private val configItem = lMediaKV.obtain<SubsonicConfig>(
        key = "SUBSONIC_CONFIG",
        defaultValue = SubsonicConfig.Empty
    ).apply { disableAutoSave() }

    private val subsonicPlugin by lazy {
        createClientPlugin("SUBSONIC_PAYLOAD_PLUGIN") {
            onRequest { request, _ ->
                request.parameter("u", configItem.value.username)
                request.parameter("v", configItem.value.version)
                request.parameter("c", configItem.value.client)
                request.parameter("f", configItem.value.format)
                request.parameter("s", configItem.value.salt)
                request.parameter("t", configItem.value.token)
            }
        }
    }
    private val ktorClient by lazy {
        HttpClient {
            install(ContentNegotiation) { json(json) }
            install(subsonicPlugin)
        }
    }
    private var subsonicApi: Pair<String, SubsonicApi>? = null

    private val actionFlow = configItem.flow().flatMapLatest { config ->
        logger.i(messageString = "config: $config")
        if (subsonicApi?.first != config.url) {
            val ktorfit = ktorfit {
                httpClient(ktorClient)
                baseUrl(config.url)
            }
            subsonicApi = config.url to ktorfit.createSubsonicApi()
        }

        val api = subsonicApi?.second
            ?: return@flatMapLatest flowOf(Snapshot.Empty)

        callbackFlow {
            // 先返回一次空，让下游能快速响应
            send(Snapshot.Empty)

            // 首先通过ping的结果来判断当前输入的配置是否正确
            val pingResp = runCatching { api.ping().response }
                .getOrNull()

            if (pingResp == null) {
                logger.i(messageString = "Request ping failed")
                return@callbackFlow
            }

            if (pingResp.isError) {
                logger.i(messageString = "Request ping failed: ${pingResp.error}")
                return@callbackFlow
            }

            // 遍历获取所有专辑
            val albums = retrieveAllPage { size, offset ->
                api.getAlbumList2(
                    type = "newest",
                    size = size,
                    offset = offset
                ).response.albumList2.album
            }

            val songs = albums.map { album ->
                async { api.getAlbum(album.id).response.album.song }
            }.awaitAll()
                .flatten()

            val audios = songs.map { song ->
                LAudio(
                    id = song.id,
                    title = song.title,
                    subtitle = song.artist,
                    mediaSourceName = this@SubsonicSource.name
                )
            }

            send(Snapshot(audios = audios))

            awaitClose {}
        }
    }

    override val dataSource: MediaDataSource = this
    override fun source(): Flow<Snapshot> = actionFlow

    override suspend fun getLyric(song: LAudio): String? = null
    override suspend fun getMedia(song: LAudio): MediaData? {
        val url = buildSubsonicUrl("stream", mapOf("id" to song.id))
        return MediaData.Url(url)
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val url = buildSubsonicUrl("getCoverArt", mapOf("id" to song.id))
        return MediaData.Url(url)
    }

    private fun buildSubsonicUrl(path: String, extras: Map<String, String>): String {
        val extraStr = extras.toList().joinToString(separator = "") { "&${it.first}=${it.second}" }
        return "${configItem.value.url}$path" +
                "?u=${configItem.value.username}" +
                "&t=${configItem.value.token}" +
                "&s=${configItem.value.salt}" +
                "&v=${configItem.value.version}" +
                "&c=${configItem.value.client}" +
                "&f=${configItem.value.format}" +
                extraStr
    }

    @Composable
    override fun Content(modifier: Modifier) {
        SubsonicSourceContent(
            modifier = modifier,
            configItem = configItem
        )
    }
}