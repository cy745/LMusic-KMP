package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.content.Context
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
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.toLegacyAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.extensions.playMode
import com.lalilu.lplayer.extensions.toMediaItem
import com.lalilu.lplayer.service.CustomCommand
import com.lalilu.lplayer.service.MService
import io.github.petertrr.diffutils.algorithm.myers.MyersDiff
import io.github.petertrr.diffutils.patch.DeltaType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class MPlayerPlayback(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val history: PlaybackHistory
) : CoroutineScope,
    Player.Listener,
    Playback,
    Runnable,
    PlaybackHistory by history {

    private val logger = Logger.withTag("MPlayerPlayback")
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    override val queue: PlayableQueue = PlayableQueueImpl()
    private var browserInstance: MediaBrowser? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(context, sessionToken)
            .buildAsync()
    }

    var pauseWhenCompletion: Boolean by mutableStateOf(false)
        private set

    // Protected mutable state flows
    private val _isPlaying = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<Throwable>()
    private val _currentDuration = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)

    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    init {
        browserFuture.addListener(this@MPlayerPlayback, Dispatchers.Main.asExecutor())
    }

    /**
     * browser 连接成功回调
     */
    override fun run() {
        val browser = browserFuture.get() ?: return
        browserInstance = browser
        browser.addListener(this@MPlayerPlayback)

        // 历史恢复
        val snapshot = restoreFromHistory()
        if (snapshot != null) {
            launch {
                val items = audioRepository.getAudios(snapshot.ids).first().map { it.toLegacyAudio() }
                queue.update { replaceAll(items, snapshot.index) }
                onQueueRestored(snapshot)
            }
        }

        // 自动录制
        startRecording(this)
    }

    override fun currentPosition(): Long {
        return runCatching { if (browserFuture.isDone) browserFuture.get()?.currentPosition else null }
            .getOrNull() ?: 0L
    }

    override suspend fun play() = runWithBrowser { play() }
    override suspend fun pause() = runWithBrowser { pause() }
    override suspend fun togglePlayPause() = runWithBrowser { if (isPlaying) pause() else play() }
    override suspend fun stop() = runWithBrowser { stop() }
    override suspend fun seekTo(positionMs: Long) = runWithBrowser { seekTo(positionMs) }

    override suspend fun skipTo(index: Int, start: Boolean) = runWithBrowser {
        if (index == -1) {
            return@runWithBrowser
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

    override suspend fun updatePlaylist(
        playlist: List<LItem>,
        startIndex: Int,
        start: Boolean
    ) = runWithBrowser {
        val items = playlist.flatMap { it.toPlayable() }
        val mediaItems = items.map { it.toMediaItem() }

        setMediaItems(mediaItems, startIndex, 0)
        if (start) play()
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

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        pauseWhenCompletion = !cancel
    }


    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) {
        val items = audioRepository.getAudios(snapshot.ids).first().map { it.toLegacyAudio() }
        val mediaIds = items.map { it.toMediaItem() }
        val browser = browserInstance ?: return

        withContext(Dispatchers.Main) {
            browser.playWhenReady = LPlayerKV.autoPlayWhenRestart.value
            browser.setMediaItems(mediaIds, snapshot.index, snapshot.position)
            browser.prepare()
        }

        // 监听播放列表更新，并刷新播放列表
        queue.expandedItems
            .filter { it.updateReason !is QueueUpdateReason.Sync }
            .onEach { (list, _) -> diffUpdateMediaItems(list) }
            .launchIn(this@MPlayerPlayback)
    }


    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateItems()

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        _currentDuration.value = mediaMetadata.durationMs ?: browserInstance?.duration ?: 0L
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
        val mediaItems = timeline?.toMediaItems() ?: emptyList()

        // 同步播放列表变化到playableQueue
        launch {
            val ids = mediaItems.map { it.mediaId }
            val items = audioRepository.getAudios(ids).first().map { it.toLegacyAudio() }

            queue.update(updateReason = QueueUpdateReason.Sync) {
                replaceAll(items = items, index = currentIndex)
            }
        }
    }

    private suspend fun diffUpdateMediaItems(items: List<LAudio>) = withContext(Dispatchers.IO) {
        val browser = browserFuture.runCatching { get() }.getOrNull() ?: return@withContext
        val currentIds = withContext(Dispatchers.Main) { browser.currentTimeline.toMediaItems().map { it.mediaId } }

        val mediaItems = items.map { it.toMediaItem() }
        val newIds = mediaItems.map { it.mediaId }
        val changes = MyersDiff<String>().computeDiff(source = currentIds, target = newIds)

        withContext(Dispatchers.Main) {
            changes.forEach { change ->
                when (change.deltaType) {
                    DeltaType.DELETE -> browser.removeMediaItems(change.startOriginal, change.endOriginal)
                    DeltaType.INSERT -> browser.addMediaItems(
                        change.startOriginal,
                        mediaItems.subList(change.startRevised, change.endRevised)
                    )

                    DeltaType.CHANGE -> browser.replaceMediaItems(
                        change.startOriginal,
                        change.endOriginal,
                        mediaItems.subList(change.startRevised, change.endRevised)
                    )

                    DeltaType.EQUAL -> {}
                }
            }
        }
    }

    private suspend fun runWithBrowser(
        block: suspend MediaBrowser.() -> Unit = {}
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
