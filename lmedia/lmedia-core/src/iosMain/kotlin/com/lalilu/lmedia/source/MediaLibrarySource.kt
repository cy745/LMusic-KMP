package com.lalilu.lmedia.source

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.buildSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItem
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusNotDetermined
import platform.MediaPlayer.MPMediaQuery

@Single(binds = [com.lalilu.lmedia.domain.source.MediaSource::class, MediaDataSource::class])
@OptIn(ExperimentalForeignApi::class)
class MediaLibrarySource : DomainMediaSource, MediaDataSource {
    override val name: String = "MediaLibrarySource"
    override val dataSource: MediaDataSource = this

    private val authorized = MutableStateFlow(false)

    init {
        val status = MPMediaLibrary.authorizationStatus()
        if (status == MPMediaLibraryAuthorizationStatusAuthorized) {
            authorized.value = true
        } else if (status == MPMediaLibraryAuthorizationStatusNotDetermined) {
            MPMediaLibrary.requestAuthorization { newStatus ->
                if (newStatus == MPMediaLibraryAuthorizationStatusAuthorized) {
                    authorized.value = true
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun source(): Flow<Snapshot> {
        return authorized.mapLatest { result ->
            if (!result) return@mapLatest Snapshot.Empty

            val items = withContext(Dispatchers.Main) {
                MPMediaQuery.songsQuery().items()
            }?.filterIsInstance<MPMediaItem>()
                ?.filter { it.assetURL != null }
                ?: return@mapLatest Snapshot.Empty

            val songs = items.map { item ->
                val urlStr: String = (item.assetURL as NSURL).let { it.absoluteString.toString() }

                LAudio(
                    id = "${LAudio.ID_PREFIX}${item.persistentID}",
                    title = item.title ?: "Unknown",
                    subtitle = item.artist ?: "Unknown Subs",
                    mediaSourceName = name,
                    metadata = Metadata(
                        title = item.title,
                        artist = item.artist,
                        album = item.albumTitle,
                        genre = item.genre ?: "",
                        track = item.albumTrackNumber.toString(),
                        disc = item.discNumber.toString(),
                        duration = (item.playbackDuration * 1000).toLong(),
                    ),
                    extra = mapOf("assetURL" to urlStr)
                )
            }

            return@mapLatest buildSnapshot(songs)
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val url = song.extra?.get("assetURL") ?: return null
        return MediaData.Url(url)
    }
}
