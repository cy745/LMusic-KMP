package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.menu.MacOSMenu
import com.lalilu.lplayer.notification.MacOSNotification
import com.lalilu.lplayer.player.VLCPlayer
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import kotlin.coroutines.CoroutineContext

class VLCPlayback : Playback, CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.io + SupervisorJob()
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

    private val errorSharedFlow = MutableSharedFlow<Throwable>()
    private val isPlayingFlow = MutableStateFlow(false)
    private val playlist = MutableStateFlow<List<LItem>>(emptyList())
    private val flattenPlaylist = playlist.flatten()
        .stateIn(this, SharingStarted.WhileSubscribed(), emptyList())

    private val currentPlaybackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val currentItemIndex = MutableStateFlow(0)
    private val currentItemFlow = flattenPlaylist
        .combine(currentItemIndex) { list, index -> list.getOrNull(index) }
        .stateIn(this, SharingStarted.WhileSubscribed(), null)

    private fun playWithItem(item: LAudio) {
        val path = item.sourceItem
            .let { it as? SourceItem.FileItem }
            ?.file?.absolutePath
            ?: throw Exception("Invalid source item: ${item.sourceItem}")

        player.media().prepare(path)
        player.controls().play()
        currentItemIndex.value = flattenPlaylist.value.indexOf(item)
    }

    override fun play() = runWith {
        if (player.media().isValid) {
            player.controls().play()
        } else {
            val current = currentItemFlow.value
                ?: throw Exception("No media to play")

            playWithItem(current)
        }
    }

    override fun pause() = runWith {
        player.controls().pause()
    }

    override fun togglePlayPause() = runWith {
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            player.controls().play()
        }
    }

    override fun stop() = runWith {
        player.controls().stop()
    }

    override fun skipTo(index: Int) = runWith {
        val targetItem = flattenPlaylist.value.getOrNull(index)
            ?: throw Exception("Invalid index")

        if (targetItem.id == currentItemFlow.value?.id) {
            seekTo(0)
        } else {
            playWithItem(targetItem)
        }
    }

    override fun skipToNext() = runWith {
        val nextIndex = (currentItemIndex.value + 1) % flattenPlaylist.value.size
        val nextItem = flattenPlaylist.value.getOrNull(nextIndex)
            ?: throw Exception("No next item")

        playWithItem(nextItem)
    }

    override fun skipTpPrevious() = runWith {
        val previousIndex = (currentItemIndex.value - 1 + flattenPlaylist.value.size) % flattenPlaylist.value.size
        val previousItem = flattenPlaylist.value.getOrNull(previousIndex)
            ?: throw Exception("No previous item")

        playWithItem(previousItem)
    }

    override fun seekTo(positionMs: Long) = runWith {
        player.controls().setTime(positionMs)
    }

    override fun flattenPlaylist(): StateFlow<List<LAudio>> = flattenPlaylist
    override fun playlist(): StateFlow<List<LItem>> = playlist

    override fun updatePlaylist(playlist: List<LItem>) {
        launch { this@VLCPlayback.playlist.emit(playlist) }
    }

    override fun clearPlaylist() {
        launch { this@VLCPlayback.playlist.emit(emptyList()) }
    }

    override fun isPlaying(): StateFlow<Boolean> = isPlayingFlow

    override fun currentItem(): StateFlow<LAudio?> = currentItemFlow

    override fun currentItemIndex(): StateFlow<Int> = currentItemIndex

    override fun currentPlaybackState(): StateFlow<PlaybackState> = currentPlaybackState

    override fun currentPosition(): Long = runWith(0) {
        player.status().time() / 1000L
    }

    override fun currentBufferedPosition(): Long = runWith(0) {
        player.status().length()
    }

    override fun errorMessage(): SharedFlow<Throwable> = errorSharedFlow
    override fun currentDuration(): Long = runWith(0) {
        player.status().length() / 1000L
    }

    private fun bindPlayer(player: MediaPlayer) {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                launch { isPlayingFlow.value = true }
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                launch { isPlayingFlow.value = false }
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                launch { skipToNext() }
            }
        })
    }

    private fun runWith(callback: () -> Unit) {
        try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
        }
    }

    private fun <T> runWith(default: T, callback: () -> T): T {
        return try {
            callback()
        } catch (e: Exception) {
            Logger.e(tag = "VLCPlayback", messageString = "${e.message}", throwable = e)
            launch { errorSharedFlow.emit(e) }
            default
        }
    }
}