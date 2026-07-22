package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.MusicKitPlayerController
import com.lalilu.lmedia.MusicKitWrapper
import com.lalilu.lmedia.SongInfo
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.*
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import platform.Foundation.NSData

@Single(binds = [MediaSource::class, MediaDataSource::class])
@OptIn(ExperimentalForeignApi::class)
class MusicKitSource : MediaSource, MediaDataSource {
    override val name: String = "MusicKitSource"
    override val dataSource: MediaDataSource = this

    /** id → SongInfo 缓存，用于 [getPicture] 和 [getMedia] 按 id 查找完整数据。 */
    private val songStore = mutableMapOf<String, SongInfo>()

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
            Logger.i(tag = name, messageString = "fetched ${songs.size} songs from MusicKit")
            songStore.clear()

            val audios = songs.map { song ->
                val playUrl = song.url()?.absoluteString ?: ""
                val storeID = song.storeID() ?: ""

                if (storeID.isBlank()) {
                    Logger.i(tag = name, messageString = "song missing storeID: ${song.title()}")
                }

                val audio = LAudio(
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
                    }
                )
                songStore[audio.id] = song
                audio
            }
            Logger.i(tag = name, messageString = "configured MusicKitPlayerController with ${songs.size} songs")
            val snapshot = buildSnapshot(audios)
            Logger.i(tag = name, messageString = "snapshot: count=${snapshot.audios?.size} state=${snapshot.state}")
            snapshot
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val storeID = songStore[song.id]?.storeID()?.takeIf { it.isNotBlank() }
            ?: song.extra?.get("storeID") as? String
        if (storeID != null) {
            // MusicKitEngine 按 mediaSourceName 路由，不需要实际的 MediaData
            return MediaData.Url("musickit://play/$storeID")
        }
        val url = song.extra?.get("url") as? String
        if (url != null && url.isNotBlank()) return MediaData.Url(url)
        return MediaData.Url("musickit://placeholder")
    }

    @Deprecated("Use getPicture(song, options) instead")
    override suspend fun getPicture(song: LAudio): MediaData? =
        getPicture(song, MediaFetchOptions.EMPTY)

    override suspend fun getPicture(song: LAudio, options: MediaFetchOptions): MediaData? {
        val storeID = songStore[song.id]?.storeID()?.takeIf { it.isNotBlank() }
            ?: song.extra?.get("storeID")
            ?: return null

        // 使用 Coil 请求的目标尺寸（若未指定则用 600 兜底）
        val w = options.width.coerceIn(60, 3000)
        val h = options.height.coerceIn(60, 3000)

        val controller = MusicKitPlayerController.shared()
        val data = controller?.artworkDataForStoreID(storeID, w.toLong(), h.toLong())
        if (data != null && data.length > 0uL) {
            return MediaData.Bytes(data.toByteArray())
        }
        return null
    }
}

/** NSData → ByteArray */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0 || bytes == null) return ByteArray(0)
    return bytes!!.readBytes(size)
}
