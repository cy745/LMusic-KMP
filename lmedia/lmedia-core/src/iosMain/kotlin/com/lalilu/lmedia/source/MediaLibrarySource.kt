package com.lalilu.lmedia.source

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import platform.MediaPlayer.MPMediaItem
import platform.MediaPlayer.MPMediaLibrary
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaQuery

object MediaLibrarySource : MediaSource {
    override val name: String = "MediaLibrarySource"
    private val authorized by lazy {
        MutableStateFlow(MPMediaLibrary.authorizationStatus() == MPMediaLibraryAuthorizationStatusAuthorized)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun source(): Flow<Snapshot> {
        return authorized.mapLatest { result ->
            if (!result) return@mapLatest Snapshot.Empty

            val items = MPMediaQuery.songsQuery().items()
                ?.filterIsInstance<MPMediaItem>()
                ?.filter { it.assetURL != null }
                ?: return@mapLatest Snapshot.Empty

            val songs = items.map {
                LAudio(
                    id = "${LAudio.ID_PREFIX}${it.persistentID}",
                    title = it.title ?: "Unknown",
                    subtitle = it.artist ?: "Unknown Subs",
                    mediaSourceName = name,
                    metadata = Metadata(
                        title = it.title,
                        artist = it.artist
                    )
                )
            }

            return@mapLatest Snapshot(audios = songs)
        }
    }
}
