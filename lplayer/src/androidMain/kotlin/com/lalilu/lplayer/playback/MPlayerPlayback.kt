package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.blankj.utilcode.util.LogUtils
import com.lalilu.lmedia.data.Library
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.flatten
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.action.Action
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.extensions.MMedia
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.extensions.playMode
import com.lalilu.lplayer.extensions.toMediaItem
import com.lalilu.lplayer.service.CustomCommand
import com.lalilu.lplayer.service.MService
import com.lalilu.lplayer.service.saveHistoryIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await

@OptIn(UnstableApi::class)
class MPlayerPlayback(
    private val context: Context,
    private val library: Library
) : AbstractPlayback(CoroutineScope(Dispatchers.Main + SupervisorJob())), Player.Listener, Runnable {
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    private var loopJob: Job? = null
    private var browserInstance: MediaBrowser? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(context, sessionToken)
            .buildAsync()
    }

    // Protected mutable state flows (now inherited from AbstractPlayback)
    // shuffledIndices and currentIndexInShuffled (now inherited from AbstractPlayback)

    private var loadedHistories: List<LAudio> = emptyList()

    init {
        browserFuture.addListener(this@MPlayerPlayback, Dispatchers.Main.asExecutor())

        launch {
            val ids = LPlayerKV.historyPlaylistIds.value
            loadedHistories = library.mapBy<LAudio>(ids)
            _playlist.value = loadedHistories

            val id = LPlayerKV.historyPlayId.value
            _currentItemIndex.value = ids.indexOf(id)
        }
    }

    /**
     * browser 连接成功回调
     */
    override fun run() {
        val browser = browserFuture.get() ?: return
        browserInstance = browser
        browser.addListener(this@MPlayerPlayback)

        val items = loadedHistories
        if (items.isEmpty()) {
            LogUtils.i("No songs found")
            return
        } else {
            LogUtils.i("Songs found: ${items.size}")
        }

        val lastPosition = LPlayerKV.historyPlayPosition.value
        val lastPlayId = LPlayerKV.historyPlayId.value
        val lastPlayIndex = _playlist.value
            .indexOfFirst { item -> item.idValue() == lastPlayId }
            .coerceIn(0, items.lastIndex)

        val mediaItems = items.map { it.toMediaItem() }
        browser.playWhenReady = LPlayerKV.autoPlayWhenRestart.value
        browser.setMediaItems(mediaItems, lastPlayIndex, lastPosition)
        browser.prepare()
    }

    fun doAction(action: Action) = launch(Dispatchers.Main) {
        val browser = browserFuture.await()

        when (action) {
            is PlayerAction.SeekTo -> {
                browser.seekTo(action.positionMs)
            }

//            is PlayerAction.CustomAction -> {}
//            is PlayerAction.PauseWhenCompletion -> {} // Handled via setPauseWhenCompletion() in PlayerAction.android.kt

            is PlayerAction.AddToNext -> {
                val item = browser.getItem(action.id).await().value ?: return@launch
                val index = browser.currentTimeline.indexOf(action.id)

                if (index != -1) {
                    val offset = if (index > browser.currentMediaItemIndex) 1 else 0
                    browser.moveMediaItem(index, browser.currentMediaItemIndex + offset)
                } else {
                    browser.addMediaItem(browser.currentMediaItemIndex + 1, item)
                }
            }

            is PlayerAction.UpdateList -> {
                val index = action.id?.let { action.ids.indexOf(it) }
                    ?.takeIf { it >= 0 }
                    ?: 0

                val items = MMedia.mapItems(action.ids)
                browser.setMediaItems(items, index, 0)
                if (action.start) {
                    browser.play()
                }
            }
        }
    }

    override fun currentPosition(): Long {
        return runCatching { if (browserFuture.isDone) browserFuture.get()?.currentPosition else null }
            .getOrNull() ?: 0L
    }

    override suspend fun play() = runWithBrowser {
        play()
    }

    override suspend fun pause() = runWithBrowser {
        pause()
    }

    override suspend fun togglePlayPause() = runWithBrowser {
        if (isPlaying) pause() else play()
    }

    override suspend fun stop() = runWithBrowser {
        stop()
    }

    // PLAY-09: skipTo(-1) delegates to skipToPrevious()
    // PLAY-07: start parameter handled by AbstractPlayback.skipTo(index,start)
    // MPlayerPlayback overrides the single-arg version for MediaBrowser API
    override suspend fun skipTo(index: Int) = runWithBrowser {
        if (index == -1) {
            // D-03: skipTo(-1) means "previous track"
            skipToPrevious()
        } else {
            seekTo(index, 0)
            play()
        }
    }

    override suspend fun skipToNext() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToNext.toSessionCommand(),
                Bundle.EMPTY
            )
        } else {
            seekToNext()
        }
    }

    override suspend fun skipToPrevious() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToPrevious.toSessionCommand(),
                Bundle.EMPTY
            )
        } else {
            seekToPrevious()
        }
    }

    override suspend fun seekTo(
        positionMs: Long
    ) = runWithBrowser {
        seekTo(positionMs)
    }

    override suspend fun updatePlaylist(
        playlist: List<LItem>
    ) = runWithBrowser {
        val items = MMedia.mapItems(playlist.map { item -> item.idValue() })
        setMediaItems(items, 0, 0)
    }

    override suspend fun updatePlaylist(
        playlist: List<LItem>,
        startIndex: Int,
        start: Boolean
    ) = runWithBrowser {
        val items = MMedia.mapItems(playlist.map { it.idValue() })
        setMediaItems(items, startIndex, 0)
        if (start) play()
    }

    override suspend fun clearPlaylist() = runWithBrowser {
        setMediaItems(emptyList())
    }

    override suspend fun setPlaybackMode(
        mode: PlaybackMode
    ) = runWithBrowser {
        playMode = when (mode) {
            PlaybackMode.SEQUENTIAL -> PlayMode.ListRecycle
            PlaybackMode.LOOP -> PlayMode.ListRecycle
            PlaybackMode.SINGLE_LOOP -> PlayMode.RepeatOne
            PlaybackMode.SHUFFLE -> PlayMode.Shuffle
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this@MPlayerPlayback._isPlaying.value = isPlaying

        loopJob?.cancel()
        if (isPlaying) {
            loopJob = launch {
                while (isActive) {
                    withContext(Dispatchers.Main) {
                        LPlayerKV.historyPlayPosition.value = currentPosition()
                    }
                    delay(1000)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackStateChanged(playbackState: Int) {

    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        _currentItemIndex.value = browserInstance?.currentMediaItemIndex ?: 0
        updateItems()

        // PLAY-01: KV persistence moved from currentItem.onEach
        mediaItem?.let {
            val id = it.mediaId
            if (id.isNotEmpty()) LPlayerKV.historyPlayId.value = id
            else LPlayerKV.historyPlayId.remove()
        }

        // PLAY-08: PauseWhenCompletion -- check inherited flag
        if (_pauseWhenCompletion) {
            browserInstance?.pause()
            _pauseWhenCompletion = false
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        _currentDuration.value = mediaMetadata.durationMs ?: browserInstance?.duration ?: 0L
        // TODO 此处获取到的duration仍然可能是上一首歌曲的时长
    }

    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {

    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updateItems(timeline)
    }

    private fun updateItems(
        timeline: Timeline? = browserInstance?.currentTimeline,
        currentIndex: Int = browserInstance?.currentMediaItemIndex ?: 0
    ) {
        val items = timeline?.toMediaItems() ?: emptyList()
        val ids = items.map { it.mediaId }

        _playlist.value = runBlocking { library.mapBy<LAudio>(ids) }
        _currentItemIndex.value = currentIndex
        saveHistoryIds(mediaIds = ids)
    }

    suspend fun runWithBrowser(
        block: MediaBrowser.() -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val browser = browserFuture.await()
        withContext(Dispatchers.Main) {
            browser.block()
        }
    }
}

private fun Timeline.toMediaItems(): List<MediaItem> {
    return (0 until this.windowCount)
        .mapNotNull { this.getWindow(it, Timeline.Window()).mediaItem }
}

private fun Timeline.indexOf(mediaId: String): Int {
    return (0 until this.windowCount).firstOrNull {
        this.getWindow(it, Timeline.Window())
            .mediaItem.mediaId == mediaId
    } ?: -1
}