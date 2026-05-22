package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.data.Library
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lplayer.menu.MacOSMenu
import com.lalilu.lplayer.notification.MacOSNotification
import com.lalilu.lplayer.player.ByteArrayCallbackMedia
import com.lalilu.lplayer.player.VLCPlayer
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class VLCPlayback(
    private val library: Library,
    private val history: PlaybackHistory
) : AbstractPlayback(history = history), KoinComponent {
    private var playerInstance: MediaPlayer? = null
    private val dataTracker: IPlaybackDataTracker by inject()

    val player: MediaPlayer
        get() = playerInstance ?: throw Exception("Player Not Initialized")

    private var lastTime: Long = 0L
    private var lastRecordTime: Long = 0L

    init {
        VLCPlayerLoader.initialize()
        VLCPlayerLoader.whenReady {
            launch {
                playerInstance = VLCPlayer.getPlayer()
                    ?.also { bindPlayer(it) }

                MacOSMenu(this@VLCPlayback)
                MacOSNotification(this@VLCPlayback)
            }
        }
    }

    override suspend fun resolveMedia(ids: List<String>): List<LAudio> = library.mapBy<LAudio>(ids)

    private suspend fun playItem(item: LAudio, start: Boolean) {
        val source = library.requireMediaSource(item.source())

        when (val data = source.dataSource.getMedia(item)) {
            is MediaData.Url -> {
                Logger.i(tag = "VLCPlayback", messageString = "prepared with url: ${data.url}")
                player.media().prepare(data.url)
            }

            is MediaData.Bytes -> {
                Logger.i(tag = "VLCPlayback", messageString = "prepared with bytes: ${data.bytes.size}")
                player.media().prepare(ByteArrayCallbackMedia.obtain(data.bytes))
            }

            else -> {
                val path = item.sourceItem
                    .let { it as? SourceItem.FileItem }
                    ?.file?.absolutePath
                    ?: throw Exception("Invalid source item: ${item.sourceItem}")

                Logger.i(tag = "VLCPlayback", messageString = "prepared with path: $path")
                player.media().prepare(path)
            }
        }
        lastRecordTime = -1
        if (start) {
            player.controls().play()
        }
        val targetIndex = queue.stateSnapshot().list.indexOfFirst { it.idValue() == item.idValue() }
        queue.update { switchTo(index = targetIndex) }
    }

    override suspend fun play() {
        try {
            if (player.media().isValid) {
                player.controls().play()
                _isPlaying.value = true
            } else {
                val current = queue.currentItem()
                    ?: throw Exception("No media to play")

                playItem(current, true)
            }
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun pause() {
        try {
            player.controls().pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun togglePlayPause() {
        try {
            if (player.status().isPlaying) {
                player.controls().pause()
                _isPlaying.value = false
            } else {
                player.controls().play()
                _isPlaying.value = true
            }
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun stop() {
        try {
            player.controls().stop()
            _isPlaying.value = false
            queue.update { switchTo(0) }
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun skipTo(index: Int, start: Boolean) {
        try {
            val state = queue.stateSnapshot()
            if (index == state.index) {
                seekTo(0)
            } else {
                val oldItem = state.currentItem()
                val targetItem = state.list.getOrNull(index)
                    ?: throw Exception("Invalid index")
                playItem(targetItem, start)

                // TODO 启动时初始化播放数据为null，导致后续无法正常更新播放时长
                dataTracker.onMediaItemTransition(
                    mediaId = targetItem.idValue(),
                    title = targetItem.titleValue(),
                    isRepeating = oldItem?.idValue() == targetItem.idValue(),
                    isNormalTransition = oldItem?.idValue() != targetItem.idValue()
                )
            }
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            lastTime = positionMs
            lastRecordTime = -1
            player.controls().setTime(positionMs)
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override fun currentPosition(): Long {
        // VLC 返回的当前播放位置并不是线性连续的，获取到的有重复的时间，所以这里通过记录播放进度的时间和当前时间的差计算实际播放位置
        if (lastRecordTime <= 0) return player.status().time()
        val delta = Clock.System.now().toEpochMilliseconds() - lastRecordTime
        return lastTime + delta
    }

    private fun bindPlayer(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                _isPlaying.value = true
                dataTracker.onIsPlayingChanged(true)
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                _isPlaying.value = false
                dataTracker.onIsPlayingChanged(false)
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                if (_pauseWhenCompletion) {
                    _pauseWhenCompletion = false
                    _isPlaying.value = false
                } else {
                    launch { skipToNext() }
                }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                lastTime = newTime
                lastRecordTime = Clock.System.now().toEpochMilliseconds()
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                _currentDuration.value = newLength
            }
        })
    }
}