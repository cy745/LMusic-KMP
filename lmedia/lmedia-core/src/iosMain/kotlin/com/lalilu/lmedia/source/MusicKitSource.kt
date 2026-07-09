package com.lalilu.lmedia.source
import com.lalilu.lmedia.domain.source.MediaSource as DomainMediaSource
import com.lalilu.lmedia.MusicKitWrapper
import com.lalilu.lmedia.SongInfo
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single(binds = [com.lalilu.lmedia.domain.source.MediaSource::class])
@OptIn(ExperimentalForeignApi::class)
class MusicKitSource : DomainMediaSource {
    override val name: String = "MusicKitSource"

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
            Snapshot(
                audios = songs.map { song ->
                    LAudio(
                        id = "${LAudio.ID_PREFIX}${song.title()}",
                        title = song.title() ?: "Unknown",
                        subtitle = song.artist() ?: "Unknown Subs",
                        mediaSourceName = name
                    )
                }
            )
        }
    }
}
