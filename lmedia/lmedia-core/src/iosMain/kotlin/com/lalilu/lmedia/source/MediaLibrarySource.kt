package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import platform.Foundation.NSURL
import platform.MediaPlayer.*

@OptIn(ExperimentalForeignApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MediaLibrarySource : MediaSource, MediaDataSource {
    override val name: String = "MediaLibrarySource"
    override val dataSource: MediaDataSource = this

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState
    private var loadingJob: Job? = null
    private var activationGeneration = 0L

    override fun init() {
        activationGeneration += 1
        val generation = activationGeneration
        when (MPMediaLibrary.authorizationStatus()) {
            MPMediaLibraryAuthorizationStatusAuthorized -> refresh(generation)
            MPMediaLibraryAuthorizationStatusNotDetermined -> {
                stateStore.content.preparing(preserveReady = false)
                MPMediaLibrary.requestAuthorization { newStatus ->
                    if (generation != activationGeneration) return@requestAuthorization
                    if (newStatus == MPMediaLibraryAuthorizationStatusAuthorized) {
                        refresh(generation)
                    } else {
                        publishPermissionDenied(generation)
                    }
                }
            }

            else -> publishPermissionDenied(generation)
        }
    }

    override suspend fun deactivate() {
        activationGeneration += 1
        loadingJob?.cancelAndJoin()
        loadingJob = null
        stateStore.reset()
        stateStore.content.unavailable("Source disabled")
    }

    private fun refresh(generation: Long) {
        if (generation != activationGeneration) return
        val taskId = stateStore.tryBegin() ?: return
        loadingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                yield()
                if (generation != activationGeneration || !stateStore.isActive(taskId)) {
                    stateStore.cancel(taskId)
                    return@launch
                }
                stateStore.content.preparing()
                if (stateStore.succeed(taskId, load()) != null) {
                    stateStore.content.ready()
                }
            } catch (cancelled: CancellationException) {
                if (stateStore.cancel(taskId)) {
                    stateStore.content.unavailable("Cancelled", preserveReady = true)
                }
                throw cancelled
            } catch (throwable: Throwable) {
                Logger.e(tag = name, throwable = throwable, messageString = "Media library scan failed")
                if (stateStore.fail(taskId, throwable.message ?: "Unknown error")) {
                    stateStore.content.unavailable(
                        throwable.message ?: "Unknown error",
                        preserveReady = true,
                    )
                }
            }
        }
    }

    private suspend fun load(): List<LAudio> {
        val items = withContext(Dispatchers.Main) {
            MPMediaQuery.songsQuery().items()
        }?.filterIsInstance<MPMediaItem>()
            ?.filter { it.assetURL != null }
            .orEmpty()

        return items.map { item ->
            val url = (item.assetURL as NSURL).absoluteString.toString()
            val artist = item.artist?.takeIf(String::isNotBlank) ?: "Unknown"
            LAudio(
                id = "${LAudio.ID_PREFIX}${item.persistentID}",
                title = item.title ?: "Unknown",
                subtitle = artist,
                mediaSourceName = name,
                extra = buildMap {
                    put("assetURL", url)
                    put("sourceId", item.persistentID.toString())
                    item.artistPersistentID.takeIf { it > 0uL }
                        ?.let { put(LAudioExtraKeys.ArtistId, it.toString()) }
                    put(LAudioExtraKeys.ArtistName, artist)
                    item.albumPersistentID.takeIf { it > 0uL }
                        ?.let { put(LAudioExtraKeys.AlbumId, it.toString()) }
                    item.albumTitle?.takeIf(String::isNotBlank)
                        ?.let { put(LAudioExtraKeys.AlbumName, it) }
                    item.albumArtist?.takeIf(String::isNotBlank)
                        ?.let { put(LAudioExtraKeys.AlbumArtist, it) }
                    item.genre?.takeIf(String::isNotBlank)
                        ?.let { put(LAudioExtraKeys.Genre, it) }
                    item.albumTrackNumber.takeIf { it > 0u }
                        ?.let { put(LAudioExtraKeys.Track, it.toString()) }
                    item.discNumber.takeIf { it > 0u }
                        ?.let { put(LAudioExtraKeys.Disc, it.toString()) }
                    (item.playbackDuration * 1000).toLong().takeIf { it > 0L }
                        ?.let { put(LAudioExtraKeys.Duration, it.toString()) }
                },
            )
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? =
        song.extra?.get("assetURL")?.let(MediaData::Url)

    private fun publishPermissionDenied(generation: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            yield()
            if (generation != activationGeneration) return@launch
            stateStore.content.unavailable("Media library permission denied")
            stateStore.failNewTask("Media library permission denied")
        }
    }

    private suspend fun MediaSourceStateStore.failNewTask(message: String) {
        fail(begin(), message)
    }
}
