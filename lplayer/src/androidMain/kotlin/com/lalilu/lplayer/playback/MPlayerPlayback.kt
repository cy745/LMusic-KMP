package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import co.touchlab.kermit.Logger
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.LPlayerKV
import com.lalilu.lplayer.extensions.PlayMode
import com.lalilu.lplayer.extensions.playMode
import com.lalilu.lplayer.extensions.toMediaItem
import com.lalilu.lplayer.service.CustomCommand
import com.lalilu.lplayer.service.MService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class MPlayerPlayback(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val mediaSourceBindingRepository: MediaSourceBindingRepository,
    private val platformMediaSource: PlatformMediaSource,
    private val history: PlaybackHistory
) : CoroutineScope,
    Player.Listener,
    Playback,
    Runnable,
    PlaybackHistory by history {

    private val logger = Logger.withTag("MPlayerPlayback")
    private val browserQueueMutex = Mutex()
    private val queueEditMutex = Mutex()
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    private val queueBridge = PlatformQueueBridge(
        applyToPlatform = { state -> diffUpdateMediaItems(state.list) },
        readPlatformAfterApply = { launch(Dispatchers.Main) { updateItems() } },
    )
    override val queue: PlayableQueue = queueBridge.queue
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
    private val recordedPosition = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    private val contentPreparation = ContentReadyPreparationCoordinator(
        scope = this,
        sourceOf = { audio ->
            platformMediaSource.findEnabledSource(audio.mediaSourceName)
        },
        onReady = { audio, playWhenReady ->
            withContext(Dispatchers.Main) {
                val browser = browserInstance ?: return@withContext
                if (browser.currentMediaItem?.mediaId != audio.id) return@withContext
                browser.playWhenReady = playWhenReady
                browser.prepare()
            }
        },
        onSourceMissing = { audio ->
            _errors.emit(
                IllegalStateException(
                    "MediaSource '${audio.mediaSourceName}' is missing or disabled"
                )
            )
        },
    )

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
        launch(Dispatchers.Main) {
            while (isActive) {
                recordedPosition.value = browser.currentPosition.coerceAtLeast(0L)
                delay(200)
            }
        }
        LPlayerKV.playMode.flow().onEach { value ->
            _playbackMode.value = when (PlayMode.from(value)) {
                PlayMode.ListRecycle -> PlaybackMode.LOOP
                PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
                PlayMode.Shuffle -> PlaybackMode.SHUFFLE
            }
        }.launchIn(this)
        startQueueMetadataRefresh(queue, audioRepository)

        // 历史恢复
        val snapshot = restoreFromHistory()
        val restorer = snapshot?.let {
            HistoryQueueRestorer(
                snapshot = it,
                repository = audioRepository,
                restoreSettled = mediaSourceBindingRepository.observeHistoryRestoreSettled(it.sourceNames.getOrNull(it.index)),
            )
        }
        restorer?.start(
            scope = this,
            queue = queue,
            onCurrentResolved = ::onQueueRestored,
        )

        // 自动录制
        startRecording(this, restorer?.state)
    }

    override fun currentPosition(): Long {
        // MediaBrowser enforces Main access. Reading it from the history IO collector used to
        // throw, and runCatching silently persisted 0ms on every tick.
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            browserInstance?.currentPosition?.coerceAtLeast(0L) ?: recordedPosition.value
        } else recordedPosition.value
    }

    override suspend fun play() {
        val current = queue.currentItem()
        if (current != null && !isContentReady(current)) {
            contentPreparation.request(current, playWhenReady = true)
            return
        }
        contentPreparation.updatePlayIntent(current?.id, playWhenReady = true)
        runWithBrowser { play() }
    }

    override suspend fun pause() {
        contentPreparation.updatePlayIntent(queue.currentItem()?.id, playWhenReady = false)
        runWithBrowser { pause() }
    }
    override suspend fun togglePlayPause() {
        if (_isPlaying.value || contentPreparation.hasPendingPlayIntent()) pause() else play()
    }

    override suspend fun stop() {
        contentPreparation.cancel()
        runWithBrowser { stop() }
    }
    override suspend fun seekTo(positionMs: Long) = runWithBrowser {
        seekTo(positionMs)
        recordedPosition.value = currentPosition.coerceAtLeast(0L)
    }

    override suspend fun skipTo(index: Int, start: Boolean) = queueEditMutex.withLock {
        if (index !in queue.stateSnapshot().list.indices) return@withLock
        queueBridge.editAndRun(block = { switchTo(index) }) {
            applySelection(it, start)
        }
    }

    override suspend fun skipToNext() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToNext.toSessionCommand(),
                Bundle.EMPTY
            ).await()
        } else {
            seekToNext()
        }
    }

    override suspend fun skipToPrevious() = runWithBrowser {
        if (playMode == PlayMode.Shuffle) {
            sendCustomCommand(
                CustomCommand.SeekToPrevious.toSessionCommand(),
                Bundle.EMPTY
            ).await()
        } else {
            seekToPrevious()
        }
    }

    override suspend fun updatePlaylist(
        playlist: List<LAudio>,
        startIndex: Int,
        start: Boolean
    ) {
        if (playlist.isEmpty()) {
            clearPlaylist()
            return
        }
        queueEditMutex.withLock {
            queueBridge.editAndRun(block = { replaceAll(playlist, startIndex.coerceIn(playlist.indices)) }) {
                applySelection(it, start)
            }
        }
    }

    override suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) = queueEditMutex.withLock {
        val previous = queue.currentItem()?.mediaKey
        val resume = isPlaying.value || contentPreparation.hasPendingPlayIntent()
        queueBridge.editAndRun(block) { selected ->
            if (selected.currentItem()?.mediaKey != previous || selected.list.isEmpty()) {
                stop()
                if (selected.list.isNotEmpty()) applySelection(selected, resume)
            }
        }
    }

    override suspend fun playAudio(audio: LAudio) = queueEditMutex.withLock {
        queueBridge.editAndRun(block = { selectOrInsert(audio) }) { selected ->
            applySelection(selected, start = true)
        }
    }

    private suspend fun applySelection(selected: QueueState, start: Boolean) {
        val audio = selected.currentItem() ?: return
        val ready = isContentReady(audio)
        contentPreparation.cancel()
        runWithBrowser {
            playWhenReady = start && ready
            seekTo(selected.index, 0)
            if (start && ready) play()
        }
        if (start && !ready) contentPreparation.request(audio, playWhenReady = true)
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


    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) = queueEditMutex.withLock {
        val restored = queue.stateSnapshot()
        if (snapshot.resolveQueue(restored.list).currentIndex != restored.index) {
            throw CancellationException("History selection was replaced")
        }
        val mediaIds = restored.list.map { it.toMediaItem() }
        val browser = browserInstance ?: return@withLock

        browserQueueMutex.withLock {
            withContext(Dispatchers.Main) {
                // 先恢复精确的队列、current 和 position，但等目标来源 Ready 后再真正打开媒体。
                browser.playWhenReady = false
                browser.setMediaItems(mediaIds, restored.index, snapshot.position)
                recordedPosition.value = snapshot.position.coerceAtLeast(0L)
            }
        }

        restored.currentItem()?.let { current ->
            contentPreparation.request(
                audio = current,
                playWhenReady = LPlayerKV.autoPlayWhenRestart.value,
            )
        }
        Unit
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        recordedPosition.value = browserInstance?.currentPosition?.coerceAtLeast(0L) ?: 0L
        _isPlaying.value = isPlaying
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        contentPreparation.cancelIfCurrentChanged(mediaItem?.mediaId)
        updateItems()

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val mediaId = browserInstance?.currentMediaItem?.mediaId
        if (mediaId == null) {
            _errors.tryEmit(error)
            return
        }

        launch(Dispatchers.Main) {
            val audio = audioRepository.getAudio(mediaId).first()
            val browser = browserInstance ?: return@launch
            if (browser.currentMediaItem?.mediaId != mediaId) return@launch
            if (audio != null && !isContentReady(audio)) {
                // Media3 的有限等待已经失败：继续在可取消协程中等待该来源，Ready 后确定性重试。
                contentPreparation.request(audio, playWhenReady = browser.playWhenReady)
            } else {
                _errors.emit(error)
            }
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
        val generation = queueBridge.newPlatformSnapshot()
        val knownItems = queue.stateSnapshot().list

        // 同步播放列表变化到playableQueue
        launch {
            val ids = mediaItems.map { it.mediaId }
            val resolved = audioRepository.getAudios(ids).first()
            // Repository results need not follow Timeline order. Preserve duplicates and never
            // compress missing entries, which would silently move the current index to another song.
            val items = resolveTimelineQueue(ids, resolved, knownItems) ?: return@launch
            queueBridge.acceptPlatformSnapshot(generation, items, currentIndex)
        }
    }

    private suspend fun diffUpdateMediaItems(items: List<LAudio>) = browserQueueMutex.withLock {
        val browser = browserFuture.await()
        val mediaItems = items.map { it.toMediaItem() }
        val newIds = mediaItems.map { it.mediaId }
        val byId = mediaItems.associateBy { it.mediaId }
        while (true) {
            val currentIds = withContext(Dispatchers.Main) {
                browser.currentTimeline.toMediaItems().map { it.mediaId }
            }
            val changes = withContext(Dispatchers.Default) { queueReplacements(currentIds, newIds) }
            val applied = withContext(Dispatchers.Main) {
                // System controls can change the Timeline while the diff is calculated off-main.
                if (browser.currentTimeline.toMediaItems().map { it.mediaId } != currentIds) {
                    return@withContext false
                }
                changes.forEach { change ->
                    browser.replaceMediaItems(change.from, change.to, change.items.map { byId.getValue(it) })
                }
                true
            }
            if (applied) break
        }
    }

    private fun isContentReady(audio: LAudio): Boolean = platformMediaSource
        .findEnabledSource(audio.mediaSourceName)
        ?.contentState
        ?.value
        ?.isReady == true

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
