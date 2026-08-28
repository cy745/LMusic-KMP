package com.lalilu.lmedia.source

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.MusicKitPlayerController
import com.lalilu.lmedia.MusicKitWrapper
import com.lalilu.lmedia.SongInfo
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.MediaDataSource
import com.lalilu.lmedia.domain.source.MediaFetchOptions
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.MediaSourceStateStore
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import platform.Foundation.NSData

@OptIn(ExperimentalForeignApi::class)
@Single(binds = [MediaSource::class, MediaDataSource::class])
class MusicKitSource : MediaSource, MediaDataSource {
    override val name: String = "MusicKitSource"
    override val dataSource: MediaDataSource = this

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val stateStore = MediaSourceStateStore()
    override val state: StateFlow<SnapshotState> = stateStore.state
    override val snapshot: StateFlow<Snapshot?> = stateStore.snapshot
    override val contentState = stateStore.contentState

    /** id → SongInfo 缓存，用于 [getPicture] 和 [getMedia] 按 id 查找完整数据。 */
    private val songStore = mutableMapOf<String, SongInfo>()

    override fun init() = refresh()

    private fun refresh() {
        scope.launch {
            val taskId = stateStore.begin()
            stateStore.content.preparing()
            MusicKitWrapper.fetchUserLibrarySongsWithCompletionHandler { songs, error ->
                scope.launch {
                    if (error != null) {
                        if (stateStore.fail(taskId, error.localizedDescription)) {
                            stateStore.content.unavailable(
                                error.localizedDescription,
                                preserveReady = true,
                            )
                        }
                        return@launch
                    }

                    val songInfos = songs?.filterIsInstance<SongInfo>().orEmpty()
                    Logger.i(tag = name, messageString = "fetched ${songInfos.size} songs from MusicKit")
                    if (stateStore.succeed(taskId, mapSongs(songInfos)) != null) {
                        stateStore.content.ready()
                    }
                }
            }
        }
    }

    private fun mapSongs(songs: List<SongInfo>): List<LAudio> {
        songStore.clear()
        return songs.map { song ->
            val title = song.title() ?: "Unknown"
            val artist = song.artist() ?: "Unknown"
            val album = song.album()
            val playUrl = song.url()?.absoluteString.orEmpty()
            val storeId = song.storeID().orEmpty()
            val identity = storeId.ifBlank { "${title}_$artist" }

            LAudio(
                id = "${LAudio.ID_PREFIX}$identity",
                title = title,
                subtitle = artist,
                mediaSourceName = name,
                extra = buildMap {
                    if (storeId.isNotBlank()) put("storeID", storeId)
                    if (playUrl.isNotBlank()) put("url", playUrl)
                    put(LAudioExtraKeys.ArtistName, artist)
                    album?.takeIf(String::isNotBlank)
                        ?.let { put(LAudioExtraKeys.AlbumName, it) }
                    (song.duration() * 1000).toLong().takeIf { it > 0L }
                        ?.let { put(LAudioExtraKeys.Duration, it.toString()) }
                },
            ).also { songStore[it.id] = song }
        }
    }

    override suspend fun getMedia(song: LAudio): MediaData? {
        val storeId = songStore[song.id]?.storeID()?.takeIf { it.isNotBlank() }
            ?: song.extra?.get("storeID")
        if (storeId != null) return MediaData.Url("musickit://play/$storeId")

        val url = song.extra?.get("url")
        if (!url.isNullOrBlank()) return MediaData.Url(url)
        return MediaData.Url("musickit://placeholder")
    }

    override suspend fun getLyric(song: LAudio): String? {
        val storeId = songStore[song.id]?.storeID()?.takeIf { it.isNotBlank() }
            ?: song.extra?.get("storeID")
            ?: return null
        return MusicKitPlayerController.shared()?.lyricsForStoreID(storeId)
    }

    @Deprecated("Use getPicture(song, options) instead")
    override suspend fun getPicture(song: LAudio): MediaData? =
        getPicture(song, MediaFetchOptions.EMPTY)

    override suspend fun getPicture(
        song: LAudio,
        options: MediaFetchOptions,
    ): MediaData? = withContext(Dispatchers.IO) {
        val songInfo = songStore[song.id]
        val storeId = songInfo?.storeID()?.takeIf { it.isNotBlank() }
            ?: song.extra?.get("storeID")
            ?: return@withContext null

        val maxWidth = songInfo?.artwork()?.maxWidth?.toInt() ?: 0
        val maxHeight = songInfo?.artwork()?.maxHeight?.toInt() ?: 0
        val width = if (options.width > 0) {
            options.width.coerceIn(60, maxWidth.coerceAtLeast(300))
        } else 300
        val height = if (options.height > 0) {
            options.height.coerceIn(60, maxHeight.coerceAtLeast(300))
        } else 300

        MusicKitPlayerController.shared()
            ?.artworkDataForStoreID(storeId, width.toLong(), height.toLong())
            ?.takeIf { it.length > 0uL }
            ?.toByteArray()
            ?.let(MediaData::Bytes)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0 || bytes == null) return ByteArray(0)
    return bytes!!.readBytes(size)
}
