package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.model.LAudio as DomainAudio
import com.lalilu.lmedia.domain.model.Metadata as DomainMetadata
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.SourceItem
import com.lalilu.lmedia.source.MediaData
import com.lalilu.lplayer.menu.MacOSMenu
import com.lalilu.lplayer.notification.MacOSNotification
import com.lalilu.lplayer.player.ByteArrayCallbackMedia
import com.lalilu.lplayer.player.VLCPlayer
import com.lalilu.lplayer.player.VLCPlayerLoader
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Single(binds = [Playback::class])
@OptIn(ExperimentalTime::class)
class VLCPlayback(
    override val audioRepository: AudioRepository,
    private val history: PlaybackHistory
) : AbstractPlayback(history = history, audioRepository = audioRepository), KoinComponent {
    private var playerInstance: MediaPlayer? = null
    private val dataTracker: IPlaybackDataTracker by inject()
    private val platformMediaSource: PlatformMediaSource by inject()

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

    private suspend fun playItem(item: LAudio, start: Boolean) {
        val source = platformMediaSource.sources.firstOrNull { item.mediaSourceName == it.name }
            ?: throw Exception("No source item found for ${item.mediaSourceName}")

        val domainAudio = item.toDomainAudio()
        when (val data = source.dataSource.getMedia(domainAudio)) {
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

private fun com.lalilu.lmedia.entity.LAudio.toDomainAudio(): DomainAudio = DomainAudio(
    id = id,
    title = title,
    subtitle = subtitle,
    mediaSourceName = mediaSourceName,
    metadata = DomainMetadata(
        title = metadata.title,
        album = metadata.album,
        artist = metadata.artist,
        albumArtist = metadata.albumArtist,
        composer = metadata.composer,
        lyricist = metadata.lyricist,
        comment = metadata.comment,
        genre = metadata.genre,
        track = metadata.track,
        disc = metadata.disc,
        date = metadata.date,
        duration = metadata.duration,
        dateAdded = metadata.dateAdded,
        dateModified = metadata.dateModified
    ),
    extra = extra,
    available = available
)
