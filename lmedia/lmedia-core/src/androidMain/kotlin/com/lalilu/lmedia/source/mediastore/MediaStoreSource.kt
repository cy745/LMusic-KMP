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
import com.lalilu.common.kv.KVItem
import com.lalilu.lmedia.LMediaKV
import com.lalilu.lmedia.Taglib
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single


@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MediaStoreSource(
    private val context: Application,
    kv: LMediaKV,
) : MediaSource, MediaDataSource {
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

    val config: KVItem<MediaStoreSourceConfig> = kv.obtain(
        key = "${name}Config",
        defaultValue = MediaStoreSourceConfig(),
    ).apply { disableAutoSave() }

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

        if (hasReadPermission()) {
            refresh()
        } else {
            stateStore.content.unavailable("Media permission denied")
        }
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun updateMinDuration(seconds: Int) {
        config.value = config.value.copy(minDurationSeconds = seconds.coerceIn(0, 60))
        config.save()
        if (initialized && hasReadPermission()) refresh()
    }

    fun cancel() {
        loadingJob?.cancel()
        stateStore.content.unavailable("Cancelled", preserveReady = true)
    }

    fun reset() {
        Logger.i(tag = name, messageString = "Reset requested")
        loadingJob?.cancel()
        stateStore.content.unavailable("Not initialized")
        loadingJob = scope.launch { stateStore.reset() }
    }

    fun refresh() {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            val taskId = stateStore.begin()
            stateStore.content.preparing()
            try {
                val minDurationMillis = config.value.minDurationSeconds * 1000L
                if (stateStore.succeed(taskId, scanner.scan(minDurationMillis)) != null) {
                    stateStore.content.ready()
                }
            } catch (cancelled: CancellationException) {
                if (stateStore.cancel(taskId)) {
                    stateStore.content.unavailable("Cancelled", preserveReady = true)
                }
                throw cancelled
            } catch (throwable: Throwable) {
                Logger.e(tag = name, throwable = throwable, messageString = "Scan failed")
                if (stateStore.fail(taskId, throwable.message ?: "Unknown error")) {
                    stateStore.content.unavailable(
                        throwable.message ?: "Unknown error",
                        preserveReady = true,
                    )
                }
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
