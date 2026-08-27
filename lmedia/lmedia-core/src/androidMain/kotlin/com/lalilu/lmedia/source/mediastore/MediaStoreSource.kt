package com.lalilu.lmedia.source.mediastore

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import com.lalilu.lmedia.source.Configurable
import com.lalilu.lmedia.source.MediaSourceConfig
import com.lalilu.lmedia.source.Saver
import com.lalilu.lmedia.source.buildConfig
import com.lalilu.lmedia.source.range
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single


@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MediaStoreSource(
    private val context: Application,
    private val saver: Saver
) : MediaSource, MediaDataSource, Configurable {
    override val name: String = "MediaStore"
    override val dataSource: MediaDataSource = this
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState
    private var loadingJob: Job? = null
    private var initialized = false

    private val scanner: Scanner = when {
        Build.VERSION.SDK_INT >= 30 -> Api30MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 29 -> Api29MediaStoreScanner(this, context)
        Build.VERSION.SDK_INT >= 21 -> Api21MediaStoreScanner(this, context)
        else -> MediaStoreScanner(this, context)
    }

    override val config: MediaSourceConfig = buildConfig(
        onConfigChange = ::onConfigChange,
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
            isAvailable = { state.value !is SnapshotState.Loading }
        ).onCall {
            Logger.i(tag = name, messageString = "On Reset")
            loadingJob?.cancel()
            loadingJob = scope.launch { stateStore.reset() }
        }

        function<Unit>(
            key = "扫描",
            description = "执行扫描",
            isAvailable = { state.value !is SnapshotState.Loading && hasReadPermission() }
        ).onCall {
            refresh()
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refresh()
        }
    }

    override fun init() {
        if (initialized) return
        initialized = true
        context.applicationContext.contentResolver
            .registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)

        if (hasReadPermission()) refresh()
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun refresh() {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            val taskId = stateStore.begin()
            try {
                stateStore.succeed(taskId, scanner.scan())
            } catch (cancelled: CancellationException) {
                stateStore.cancel(taskId)
                throw cancelled
            } catch (throwable: Throwable) {
                Logger.e(tag = name, throwable = throwable, messageString = "Scan failed")
                stateStore.fail(taskId, throwable.message ?: "Unknown error")
            }
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        if (song.mediaSourceName != name) return null
        val uri = song.extra?.get("uri") ?: return null
        return MediaData.Url(uri)
    }

    override suspend fun getLyric(song: LAudio): String? = withContext(Dispatchers.IO) {
        if (song.mediaSourceName != name) return@withContext null
        val uriStr = song.extra?.get("uri") ?: return@withContext null
        context.contentResolver
            .openFileDescriptor(uriStr.toUri(), "r")
            ?.use { Taglib.getLyric(fd = it.detachFd()) }
    }

    override suspend fun getPicture(song: LAudio): MediaData? = withContext(Dispatchers.IO) {
        if (song.mediaSourceName != name) return@withContext null
        val uriStr = song.extra?.get("uri") ?: return@withContext null
        val picture = context.contentResolver
            .openFileDescriptor(uriStr.toUri(), "r")
            ?.use { Taglib.getPicture(fd = it.detachFd()) }
            ?: throw IllegalArgumentException("Picture not found for $uriStr")
        MediaData.Bytes(picture)
    }
}
