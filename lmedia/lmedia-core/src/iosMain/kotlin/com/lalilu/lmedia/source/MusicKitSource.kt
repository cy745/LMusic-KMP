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
import com.lalilu.lmedia.domain.source.requireContentReady
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
                    // succeed 仅在任务仍为当前任务时发布快照（被更新任务取代则不发布）；
                    // 但 Swift 侧 songCache 已重新配置成功，内容读取能力已就绪，
                    // 必须无条件 ready()，否则封面/歌词可能永远等不到就绪或 generation 不递增。
                    stateStore.succeed(taskId, mapSongs(songInfos))
                    stateStore.content.ready()
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

        // 封面分辨率：Apple Music CDN（mzstatic）支持按需放大，
        // artwork.maximumWidth 仅是 Apple 报告的源图尺寸（常见 600，部分为 0），
        // 若以此钳制会导致大封面（播放页）模糊。统一按目标/高清请求，
        // Coil 解码时会按显示尺寸缩放，内存可控。
        val size = when {
            options.width > 0 || options.height > 0 ->
                maxOf(options.width, options.height).coerceIn(200, 3000)
            else -> 1200
        }
        val width = size
        val height = size

        MusicKitPlayerController.shared()
            ?.artworkDataForStoreID(storeId, width.toLong(), height.toLong())
            ?.takeIf { it.length > 0uL }
            ?.toByteArray()
            ?.let(MediaData::Bytes)
            ?.let { return@withContext it }

        // 兜底：内容未就绪（App 重启后 fetch 尚未完成、Swift 侧 songCache 未配置时
        // 首查必然 miss）。等待数据源内容就绪（fetch 成功并重新配置 songCache）后重试；
        // 授权失败/网络失败（Unavailable）或超时则返回 null，由上层显示占位。
        runCatching { requireContentReady(timeoutMillis = 15_000) }
            .getOrNull()
            ?.takeIf { it.isReady }
            ?.let {
                MusicKitPlayerController.shared()
                    ?.artworkDataForStoreID(storeId, width.toLong(), height.toLong())
                    ?.takeIf { it.length > 0uL }
                    ?.toByteArray()
                    ?.let(MediaData::Bytes)
            }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0 || bytes == null) return ByteArray(0)
    return bytes!!.readBytes(size)
}
