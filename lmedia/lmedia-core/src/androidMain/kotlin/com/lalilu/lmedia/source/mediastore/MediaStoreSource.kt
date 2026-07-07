package com.lalilu.lmedia.source.mediastore

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.entity.*
import com.lalilu.lmedia.source.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Single


@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MediaStoreSource(
    private val context: Application,
    private val saver: Saver
) : MediaSource, MediaDataSource {
    override val name: String = "MediaStore"
    private val scanner = when {
        Build.VERSION.SDK_INT >= 30 -> Api30MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 29 -> Api29MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 21 -> Api21MediaStoreScanner(this, context)
        else -> MediaStoreScanner(this, context)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override val dataSource: MediaDataSource = this
    private val stateFlow = MutableStateFlow(Snapshot.Idle)
    private val stateValue by stateFlow.toComposeState(scope)
    override fun source(): Flow<Snapshot> = stateFlow


    override val config: MediaSourceConfig = buildConfig(
        key = name,
        name = "MediaStore",
        description = "通过 MediaStore 扫描音频文件",
        saver = saver
    ) {
        property<Int>(
            key = "min_duration",
            name = "最小时长",
            description = "最小时长（秒），低于此值将忽略"
        ).provide(10)
            .range(min = 0, max = 60)

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            stateFlow.value = stateFlow.value.copy(state = SnapshotState.Idle)
        }

        function<Unit>(
            key = "扫描",
            description = "执行扫描",
            isAvailable = { stateValue.let { it !is SnapshotState.Loading && it !is SnapshotState.LoadingDynamic } }
        ).onCall {
            scope.launch {
                stateFlow.value = Snapshot.Loading
                stateFlow.value = scanner.scan()
            }
        }
    }

    init {
        scope.launch { stateFlow.value = scanner.scan() }

        // 注册 ContentObserver，监听 MediaStore 变化后重新扫描
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch { stateFlow.value = scanner.scan() }
            }
        }

        context.applicationContext.contentResolver
            .registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        if (song.source() != name) return null
        val audio = stateFlow.value.audios.firstOrNull { it.idValue() == song.idValue() }

        val sourceItem = audio?.sourceItem
            ?: throw IllegalArgumentException("Invalid id: ${song.idValue()}")

        return when (sourceItem) {
            is SourceItem.FileItem -> {
                val file = sourceItem.file
                MediaData.Bytes(file.readBytes())
            }

            is SourceItem.FilePathItem -> {
                MediaData.Url(sourceItem.path)
            }

            is SourceItem.UriItem -> {
                MediaData.Url(sourceItem.uri.toString())
            }

            else -> null
        }
    }
}
