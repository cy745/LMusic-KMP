package com.lalilu.lmedia.source

import com.lalilu.lmedia.MusicKitWrapper
import com.lalilu.lmedia.SongInfo
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.buildSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(binds = [com.lalilu.lmedia.domain.source.MediaSource::class, MediaDataSource::class])
@OptIn(ExperimentalForeignApi::class)
class MusicKitSource : DomainMediaSource, MediaDataSource {
    override val name: String = "MusicKitSource"
    override val dataSource: MediaDataSource = this

    override fun source(): Flow<Snapshot> {
        val songsFlow = callbackFlow {
            MusicKitWrapper.fetchUserLibrarySongsWithCompletionHandler { songs, error ->
                launch {
                    send(songs?.filterIsInstance<SongInfo>() ?: emptyList())
                }
            }
            awaitClose {}
        }

        return songsFlow.map { songs ->
            val audios = songs.map { song ->
                    val playUrl = song.url()?.absoluteString ?: ""
                    val artworkUrl = song.artwork()
                        ?.urlWithWidth(512, 512)
                        ?.absoluteString
                        ?: ""
                    val storeID = song.storeID() ?: ""

                    LAudio(
                        id = "${LAudio.ID_PREFIX}${song.title()}_${song.artist()}",
                        title = song.title() ?: "Unknown",
                        subtitle = song.artist() ?: "Unknown Subs",
                        mediaSourceName = name,
                        metadata = Metadata(
                            title = song.title(),
                            artist = song.artist(),
                            album = song.album(),
                            duration = (song.duration() * 1000).toLong(),
                        ),
                        extra = buildMap {
                            if (storeID.isNotBlank()) put("storeID", storeID)
                            if (playUrl.isNotBlank()) put("url", playUrl)
                            if (artworkUrl.isNotBlank()) put("artworkUrl", artworkUrl)
                        }
                    )
                }
            buildSnapshot(audios)
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val url = song.extra?.get("url") ?: return null
        return MediaData.Url(url)
    }

    override suspend fun getPicture(song: LAudio): MediaData? {
        val artworkUrl = song.extra?.get("artworkUrl") ?: return null
        return MediaData.Url(artworkUrl)
    }
}
