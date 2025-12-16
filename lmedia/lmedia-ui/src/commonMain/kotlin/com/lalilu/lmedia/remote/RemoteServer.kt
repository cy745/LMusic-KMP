package com.lalilu.lmedia.remote

import androidx.compose.runtime.mutableStateOf
import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.common.kv.KVContext
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.server.entity.RemoteServerConfig
import com.lalilu.lmedia.server.LMediaServer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext


@Single(createdAtStart = true)
class RemoteServer(
    @Named("LMediaKV")
    private val kv: KVContext,
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


    val config by lazy { kv.obtain<RemoteServerConfig>(CONFIG_KEY) }
    val running by lazy { mutableStateOf(false) }

    init {
        config.flow()
            .onEach {
                val server = LMediaServer(
                    config = it,
                    sources = sources,
                    json = json
                )
                server.startSync()
                server.stopAndRelease()
            }
            .launchIn(this)
    }
}