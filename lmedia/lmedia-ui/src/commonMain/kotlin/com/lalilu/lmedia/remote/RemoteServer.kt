package com.lalilu.lmedia.remote

import androidx.compose.runtime.mutableStateOf
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.server.LMediaServer
import com.lalilu.lmedia.server.entity.RemoteServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single(createdAtStart = true)
class RemoteServer(
    private val kv: LMediaKV,
    private val sources: PlatformMediaSource,
    private val json: Json
) : CoroutineScope {
    private val logger = KotlinLogging.logger(TAG)

    companion object {
        const val TAG = "RemoteServer"
        const val CONFIG_KEY = "REMOTE_SERVER_CONFIG"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            logger.error(throwable) {}
        }

    val config by lazy { kv.obtain<RemoteServerConfig>(CONFIG_KEY, RemoteServerConfig.Empty) }
    val running by lazy { mutableStateOf(false) }
    private var server: LMediaServer? = null

    init {
        config.flow()
            .onEach(::startServer)
            .launchIn(this)
    }

    private suspend fun startServer(
        config: RemoteServerConfig
    ) = withContext(Dispatchers.Unconfined) {
        running.value = false
        server?.stopAndRelease()
        if (server != null) {
            logger.info { "Remote server is stopping" }
        }

        if (!config.enable) {
            logger.info { "Remote server is disabled" }
            return@withContext
        }

        runCatching {
            server = LMediaServer(
                config = config,
                sources = sources,
                json = json
            )

            running.value = true
            server?.startAsync()
            logger.info { "Remote server is started" }
        }.getOrElse {
            running.value = false
            logger.error(it) { "Remote server is failed to start" }
        }
    }
}