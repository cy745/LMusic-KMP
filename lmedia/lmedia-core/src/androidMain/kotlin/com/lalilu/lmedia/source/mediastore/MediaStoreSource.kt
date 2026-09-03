package com.lalilu.lmedia.source.mediastore

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.system.Os
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
import com.lalilu.lmedia.source.external.ExternalMediaMatch
import com.lalilu.lmedia.source.external.ExternalMediaMatchBasis
import com.lalilu.lmedia.source.external.ExternalMediaMatcher
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single


@OptIn(ExperimentalCoroutinesApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MediaStoreSource(
    private val context: Application,
    kv: LMediaKV,
) : MediaSource, MediaDataSource, ExternalMediaMatcher {
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

    override suspend fun matchExternalMedia(
        file: PlatformFile,
        candidates: List<LAudio>,
    ): ExternalMediaMatch? = withContext(Dispatchers.IO) {
        val incoming = (file.androidFile as? AndroidFile.UriWrapper)?.uri
            ?: return@withContext null
        if (incoming.scheme != "content") return@withContext null

        val mediaUri = when {
            incoming.authority == MediaStore.AUTHORITY -> incoming
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                runCatching { MediaStore.getMediaUri(context, incoming) }.getOrNull()
            }
            else -> null
        }

        mediaUri
            ?.takeIf { it.authority == MediaStore.AUTHORITY && "audio" in it.pathSegments }
            ?.let { findKnownAudio(it, candidates) }
            ?.let { return@withContext ExternalMediaMatch(it, ExternalMediaMatchBasis.SourceLocator) }

        findProviderBackedMedia(incoming, candidates)?.let {
            Logger.i(tag = name, messageString = "Matched provider URI through MediaStore fallback: $incoming")
            ExternalMediaMatch(it, ExternalMediaMatchBasis.SourceLocator)
        }
    }

    private fun findKnownAudio(mediaUri: Uri, candidates: List<LAudio>): LAudio? {
        val mediaId = runCatching {
            context.contentResolver.query(
                mediaUri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            }
        }.getOrNull() ?: runCatching { ContentUris.parseId(mediaUri) }.getOrNull()
            ?: return null

        val normalizedUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaId,
        ).toString()
        val expectedId = "${LAudio.ID_PREFIX}$normalizedUri"
        return candidates.firstOrNull {
            it.mediaSourceName == name &&
                (it.id == expectedId || it.extra?.get("uri") == mediaUri.toString())
        } ?: snapshot.value?.audios?.firstOrNull {
            it.id == expectedId || it.extra?.get("uri") == mediaUri.toString()
        }
    }

    private fun findProviderBackedMedia(incoming: Uri, candidates: List<LAudio>): LAudio? {
        val descriptor = queryExternalDescriptor(incoming) ?: return null
        val displayName = descriptor.displayName ?: return null
        val knownAudios = (candidates + snapshot.value?.audios.orEmpty())
            .asSequence()
            .filter { it.mediaSourceName == name }
            .distinctBy { it.id }
            .toList()
        if (knownAudios.isEmpty()) return null

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
        val rows = runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                buildList {
                    while (cursor.moveToNext()) {
                        val mediaUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idIndex),
                        )
                        val audio = findKnownAudio(mediaUri, knownAudios) ?: continue
                        add(
                            MediaStoreFallbackCandidate(
                                value = audio,
                                displayName = cursor.getString(nameIndex),
                                size = cursor.getLongOrNull(sizeIndex),
                                relativePath = cursor.getStringOrNull(relativePathIndex),
                                identity = queryFileIdentity(mediaUri),
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrElse { throwable ->
            Logger.w(tag = name, throwable = throwable, messageString = "MediaStore fallback query failed")
            emptyList()
        }

        return matchMediaStoreFallback(descriptor, rows)
    }

    private fun queryExternalDescriptor(uri: Uri): ExternalMediaDescriptor? {
        var displayName: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getStringOrNull(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    )
                    size = cursor.getLongOrNull(cursor.getColumnIndex(OpenableColumns.SIZE))
                }
            }
        }
        displayName = displayName ?: uri.lastPathSegment?.substringAfterLast('/')
        if (displayName.isNullOrBlank()) return null

        return ExternalMediaDescriptor(
            displayName = displayName,
            size = size,
            relativePath = inferExternalRelativePath(uri.pathSegments),
            identity = queryFileIdentity(uri),
        )
    }

    private fun queryFileIdentity(uri: Uri): LocalFileIdentity? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            Os.fstat(descriptor.fileDescriptor).let { stat ->
                LocalFileIdentity(device = stat.st_dev, inode = stat.st_ino)
            }
        }
    }.getOrNull()

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (index >= 0 && !isNull(index)) getLong(index) else null

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
