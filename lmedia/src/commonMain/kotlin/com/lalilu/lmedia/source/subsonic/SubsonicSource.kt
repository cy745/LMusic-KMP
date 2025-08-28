package com.lalilu.lmedia.source.subsonic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

        flow {
            // 首先通过ping的结果来判断当前输入的配置是否正确
            val pingResp = runCatching { api.ping().response }
                .getOrNull()

            if (pingResp == null) {
                emit(Snapshot.Empty)
                logger.i(messageString = "Request ping failed")
                return@flow
            }

            if (pingResp.isError) {
                emit(Snapshot.Empty)
                logger.i(messageString = "Request ping failed: ${pingResp.error}")
                return@flow
            }

            emit(Snapshot.Empty)
        }
    }

    override fun source(): Flow<Snapshot> = actionFlow

    override suspend fun getLyric(song: LAudio): String? = null
    override suspend fun getMedia(song: LAudio): MediaData? {
        return null
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        return null
    }

    @Composable
    override fun Content(modifier: Modifier) {
        super.Content(modifier)
    }
}
