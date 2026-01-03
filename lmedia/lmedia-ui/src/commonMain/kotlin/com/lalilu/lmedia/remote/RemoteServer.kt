package com.lalilu.lmedia.remote

import androidx.compose.runtime.mutableStateOf
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.server.LMediaServer
import com.lalilu.lmedia.server.entity.RemoteServerConfig
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
    companion object {
        const val TAG = "RemoteServer"
        const val CONFIG_KEY = "REMOTE_SERVER_CONFIG"
    }

    override val coroutineContext: CoroutineContext =
        Dispatchers.io + SupervisorJob() + CoroutineExceptionHandler { context, throwable ->
            Logger.e(TAG, throwable)
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
            Logger.i(tag = TAG, messageString = "Remote server is stopping")
        }

        if (!config.enable) {
            Logger.i(tag = TAG, messageString = "Remote server is disabled")
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
            Logger.i(tag = TAG, messageString = "Remote server is started")
        }.getOrElse {
            running.value = false
            Logger.e(tag = TAG, messageString = "Remote server is failed to start", throwable = it)
        }
    }
}