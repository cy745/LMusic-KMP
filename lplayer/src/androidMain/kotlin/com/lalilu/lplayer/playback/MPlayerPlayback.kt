package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.source.Library
import com.lalilu.lmedia.util.flatten
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.action.Action
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.lplayer.extensions.MMedia
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.extensions.playMode
import com.lalilu.lplayer.service.CustomCommand
import com.lalilu.lplayer.service.MService
import com.lalilu.lplayer.service.getHistoryItems
import com.lalilu.lplayer.service.saveHistoryIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlin.coroutines.CoroutineContext

@OptIn(UnstableApi::class)
class MPlayerPlayback(
    private val library: Library
) : CoroutineScope, Player.Listener, Playback {
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(Utils.getApp(), ComponentName(Utils.getApp(), MService::class.java))
    }

    private var loopJob: Job? = null
    private var browserInstance: MediaBrowser? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(Utils.getApp(), sessionToken)
            .buildAsync()
    }

    var pauseWhenCompletion: Boolean by mutableStateOf(false)
        private set

    // Protected mutable state flows
    private val _playlist = MutableStateFlow<List<LItem>>(emptyList())
    private val _currentItemIndex = MutableStateFlow(0)
    private val _isPlaying = MutableStateFlow(false)
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val _errors = MutableSharedFlow<Throwable>()
    private val _currentDuration = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _canSeek = MutableStateFlow(false)
    private val _canSkipNext = MutableStateFlow(false)
    private val _canSkipPrevious = MutableStateFlow(false)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    private var shuffledIndices: List<Int> = emptyList()
    private var currentIndexInShuffled: Int = 0

    // Public state flows
    override val playlist: StateFlow<List<LItem>> = _playlist.asStateFlow()
    override val currentItemIndex: StateFlow<Int> = _currentItemIndex.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val canSeek: StateFlow<Boolean> = _canSeek.asStateFlow()
    override val canSkipNext: StateFlow<Boolean> = _canSkipNext.asStateFlow()
    override val canSkipPrevious: StateFlow<Boolean> = _canSkipPrevious.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val flattenedPlaylist: StateFlow<List<LAudio>> = _playlist
        .flatten()
        .stateIn(this, SharingStarted.WhileSubscribed(), emptyList())

    // Computed properties
    override val currentItem: StateFlow<LAudio?> = flattenedPlaylist
        .combine(_currentItemIndex) { playlist, index -> playlist.getOrNull(index) }
        .stateIn(this, SharingStarted.WhileSubscribed(), null)

    init {
        launch(Dispatchers.Main) {
            preInit()
            library.whenReady {
                launch(Dispatchers.Main) {
                    onLibraryReady()
                }
            }
        }
    }

    internal suspend fun preInit() {
        val browser = browserFuture.await()
        browserInstance = browser
        browser.addListener(this@MPlayerPlayback)
    }

    internal suspend fun onLibraryReady() {
        val browser = browserFuture.await()
        val lastPosition = LPlayerKV.historyPlayPosition.value
        val items = getHistoryItems()
        if (items.isEmpty()) {
            LogUtils.i("No songs found")
            return
        }

        browser.playWhenReady = LPlayerKV.autoPlayWhenRestart.value
        browser.setMediaItems(items, 0, lastPosition)
        browser.prepare()
    }

    fun doAction(action: Action) = launch(Dispatchers.Main) {
        val browser = browserFuture.await()

        when (action) {
            is PlayerAction.SeekTo -> {
                browser.seekTo(action.positionMs)
            }

//            is PlayerAction.CustomAction -> {}
            is PlayerAction.PauseWhenCompletion -> {
                pauseWhenCompletion = !action.cancel
            }

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

    override suspend fun skipTo(
        index: Int
    ) = runWithBrowser {
        if (index == -1) {
            // TODO
//            val item = browser.getItem(id)
//                .await().value ?: return
//
//            browser.addMediaItem(0, item)
//            browser.prepare()
//            browser.play()
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
        val items = MMedia.mapItems(playlist.map { item -> item.id })
        setMediaItems(items, 0, 0)
    }

    override suspend fun updatePlaylist(
        playlist: List<LItem>,
        startIndex: Int,
        start: Boolean
    ) = runWithBrowser {
        val items = MMedia.mapItems(playlist.map { it.id })
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

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
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

        _playlist.value = library.mapBy<LAudio>(ids)
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