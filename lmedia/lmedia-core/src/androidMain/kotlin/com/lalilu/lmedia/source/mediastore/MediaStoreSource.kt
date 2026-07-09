package com.lalilu.lmedia.source.mediastore

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.MediaSource
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.Saver
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.source.range
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
    override val dataSource: MediaDataSource = this
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateFlow = MutableStateFlow(Snapshot.Idle)
    override fun source(): Flow<Snapshot> = stateFlow

    private val scanner: Scanner = when {
        Build.VERSION.SDK_INT >= 30 -> Api30MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 29 -> Api29MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 21 -> Api21MediaStoreScanner(this, context)
        else -> MediaStoreScanner(this, context)
    }

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
            .range(0, 60, 0)

        function<Unit>(
            key = "Reset",
            description = "重置",
            isAvailable = { stateFlow.value.state !is SnapshotState.Loading }
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            stateFlow.value = Snapshot.Idle
        }

        function<Unit>(
            key = "扫描",
            description = "执行扫描",
            isAvailable = { stateFlow.value.state !is SnapshotState.Loading }
        ).onCall {
            scope.launch {
                stateFlow.value = Snapshot.Loading
                stateFlow.value = scanner.scan()
            }
        }
    }

    init {
        scope.launch { stateFlow.value = scanner.scan() }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch { stateFlow.value = scanner.scan() }
            }
        }

        context.applicationContext.contentResolver
            .registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        if (song.mediaSourceName != name) return null
        val uri = song.extra?.get("uri") ?: return null
        return MediaData.Url(uri)
    }
}
