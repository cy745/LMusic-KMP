package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lmedia.util.flatten
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

class VLCPlayback : AbstractPlayback(), KoinComponent {
    private val platformMediaSource: PlatformMediaSource by inject()
    private var playerInstance: MediaPlayer? = null
    val player: MediaPlayer
        get() = playerInstance ?: throw Exception("Player Not Initialized")

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

    override suspend fun playItem(item: LAudio) {
        val source = platformMediaSource.sources
            .firstOrNull { item.mediaSourceName == it.name }
            ?: throw Exception("No source item found for ${item.mediaSourceName}")

        val data = source.dataSource.getMedia(item)
        when (data) {
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
        player.controls().play()
        _currentItemIndex.value = _playlist.value.flatten().indexOf(item)
        updateNavigationCapabilities()
    }

    override suspend fun play() {
        try {
            if (player.media().isValid) {
                player.controls().play()
                _isPlaying.value = true
            } else {
                val current = currentItem.value
                    ?: throw Exception("No media to play")

                playItem(current)
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
            _currentItemIndex.value = 0
            updateNavigationCapabilities()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun skipTo(index: Int) {
        try {
            val targetItem = playlist.value.flatten().getOrNull(index)
                ?: throw Exception("Invalid index")

            if (targetItem.id == currentItem.value?.id) {
                seekTo(0)
            } else {
                playItem(targetItem)
            }
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        try {
            player.controls().setTime(positionMs)
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            emitError(e)
        }
    }
    
    override fun currentPosition(): Long {
        return player.status().time()
    }

    private fun bindPlayer(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                _isPlaying.value = true
                currentItem.value?.let { item ->
                    _playbackState.value = PlaybackState.Playing(item)
                }
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                _isPlaying.value = false
                currentItem.value?.let { item ->
                    _playbackState.value = PlaybackState.Paused(item)
                }
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                launch { skipToNext() }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                // 不再需要更新_currentPosition
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                _currentDuration.value = newLength / 1000L
                updateNavigationCapabilities()
            }
        })
    }
}