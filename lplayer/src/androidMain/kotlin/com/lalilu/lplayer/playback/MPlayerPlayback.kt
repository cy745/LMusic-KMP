package com.lalilu.lplayer.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
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
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
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
    private val history: PlaybackHistory,
    private val playbackFailureRepository: PlaybackFailureRepository,
    private val navigation: AndroidPlaybackNavigation,
) : CoroutineScope,
    Player.Listener,
    Playback,
    HistoryPositionProvider,
    Runnable,
    PlaybackHistory by history {

    private val logger = Logger.withTag("MPlayerPlayback")
    private val browserQueueMutex = Mutex()
    private val queueEditMutex = Mutex()
    private val failureTraversal = navigation.failures
    private val failureWrites = PlaybackFailureWrites(playbackFailureRepository)
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    private val queueBridge = PlatformQueueBridge(
        applyToPlatform = { state ->
            if (state.updateReason == QueueUpdateReason.HistoryRestore) applyHistoryQueue(state)
            else diffUpdateMediaItems(state.list)
        },
        readPlatformAfterApply = { launch(Dispatchers.Main) { updateItems() } },
    )
    override val queue: PlayableQueue = queueBridge.queue
    private var browserInstance: MediaBrowser? = null
    private var startupHistory: PlaybackHistory.HistorySnapshot? = null
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
        preparationTicket = { navigation.preparation.ticket() },
        onReady = { audio, latestIntent ->
            withContext(Dispatchers.Main) {
                val browser = browserInstance ?: return@withContext
                if (browser.currentMediaItem?.mediaId != audio.playbackId) return@withContext
                val (playWhenReady, ticket) = latestIntent() ?: return@withContext
                val intent = navigation.preparation.playIntent(ticket, playWhenReady) ?: return@withContext
                browser.playWhenReady = intent
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
                PlayMode.Sequential -> PlaybackMode.SEQUENTIAL
                PlayMode.ListRecycle -> PlaybackMode.LOOP
                PlayMode.RepeatOne -> PlaybackMode.SINGLE_LOOP
                PlayMode.Shuffle -> PlaybackMode.SHUFFLE
            }
        }.launchIn(this)
        startQueueMetadataRefresh(queue, audioRepository)

        // 历史恢复
        val snapshot = restoreFromHistory()
        startupHistory = snapshot
        val restorer = snapshot?.let {
            HistoryQueueRestorer(
                snapshot = it,
                repository = audioRepository,
                restoreSettled = mediaSourceBindingRepository.observeHistoryRestoreSettled(it.sourceNames.getOrNull(it.index)),
                readableSources = mediaSourceBindingRepository.observeReadableSources(),
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

    override suspend fun historyPosition(expectedQueue: QueueState): Long? = withContext(Dispatchers.Main) {
        val browser = browserInstance ?: return@withContext null
        if (queue.stateSnapshot() !== expectedQueue) return@withContext null
        if (!matchesNativeHistorySelection(
                expectedQueue,
                browser.currentTimeline.toMediaItems().map { it.mediaId },
                browser.currentMediaItemIndex,
            )) return@withContext null
        browser.currentPosition.takeIf { it >= 0L }
    }

    override suspend fun play() {
        val current = queue.currentItem()
        if (current != null && !isContentReady(current)) {
            contentPreparation.request(current, playWhenReady = true)
            return
        }
        contentPreparation.updatePlayIntent(current?.playbackId, playWhenReady = true)
        runWithBrowser { play() }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) { failureTraversal.cancel() }
        contentPreparation.updatePlayIntent(queue.currentItem()?.playbackId, playWhenReady = false)
        runWithBrowser { pause() }
    }
    override suspend fun togglePlayPause() {
        if (_isPlaying.value || contentPreparation.hasPendingPlayIntent()) pause() else play()
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) { failureTraversal.cancel() }
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
        failureTraversal.begin(PlaybackDirection.Forward)
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
        failureTraversal.begin(PlaybackDirection.Backward)
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

    override suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) = editQueueWithReceipt(block) {}

    override suspend fun editQueueWithReceipt(block: QueueUpdateRequest.() -> Unit, onApplied: (QueueState) -> Unit) = queueEditMutex.withLock {
        val previous = queue.currentItem()?.mediaKey
        val resume = isPlaying.value || contentPreparation.hasPendingPlayIntent()
        queueBridge.editAndRun(block) { selected ->
            if (selected.currentItem()?.mediaKey != previous || selected.list.isEmpty()) {
                stop()
                if (selected.list.isNotEmpty()) applySelection(selected, resume)
            }
        }
        onApplied(queue.stateSnapshot())
    }

    override suspend fun playAudio(audio: LAudio) = queueEditMutex.withLock {
        queueBridge.editAndRun(block = { selectOrInsert(audio) }) { selected ->
            applySelection(selected, start = true)
        }
    }

    override suspend fun restoreFailedQueueEdit(expected: QueueState, original: QueueState, position: Long): Boolean =
        queueEditMutex.withLock {
            val browser = browserInstance ?: return@withLock false
            withContext(Dispatchers.Main) {
                restorePausedQueueIfOwned(
                    bridge = queueBridge, player = browser, expected = expected, original = original,
                    position = position, prepare = original.currentItem()?.let(::isContentReady) == true,
                    beforeApply = {
                        failureTraversal.cancel()
                        contentPreparation.cancel()
                    },
                ).also { applied -> if (applied) recordedPosition.value = position.coerceAtLeast(0) }
            }
        }

    override suspend fun playNext(audio: LAudio) = queueEditMutex.withLock {
        queueBridge.editAndRun(block = {
            if (queue.stateSnapshot().list.none { it.mediaKey == audio.mediaKey }) addToNext(listOf(audio))
        }) {
            runWithBrowser {
                sendCustomCommand(CustomCommand.PlayNext.toSessionCommand(), Bundle().apply {
                    putString("playbackId", audio.playbackId)
                }).await()
            }
        }
    }

    private suspend fun applySelection(selected: QueueState, start: Boolean) {
        withContext(Dispatchers.Main) { failureTraversal.begin() }
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
            PlaybackMode.SEQUENTIAL -> PlayMode.Sequential
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
        if (isPlaying) {
            val playedId = browserInstance?.currentMediaItem?.mediaId
            if (playedId != null) {
                val ticket = failureWrites.register(playedId)
                launch {
                    failureWrites.apply(ticket, null).onFailure {
                        Logger.e(it) { "Could not clear playback failure" }
                    }
                }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            failureTraversal.begin(PlaybackDirection.Forward)
        }
        contentPreparation.cancelIfCurrentChanged(mediaItem?.mediaId)
        updateItems()

        if (pauseWhenCompletion) {
            browserInstance?.pause()
            pauseWhenCompletion = false
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val failureTicket = failureTraversal.generation
        val failedSelectionRevision = queue.stateSnapshot().selectionRevision
        val failedBrowser = browserInstance
        val mediaId = failedBrowser?.currentMediaItem?.mediaId
        if (mediaId == null) {
            _errors.tryEmit(error)
            return
        }
        val failedIndex = failedBrowser.currentMediaItemIndex
        val failedIds = failedBrowser.currentTimeline.toMediaItems().map { it.mediaId }
        val writeTicket = failureWrites.register(mediaId)

        launch(Dispatchers.Main) {
            fun isCurrentFailure(): Boolean = browserInstance === failedBrowser &&
                failureTicket == failureTraversal.generation &&
                queue.stateSnapshot().selectionRevision == failedSelectionRevision &&
                failedBrowser.currentMediaItem?.mediaId == mediaId &&
                failedBrowser.currentMediaItemIndex == failedIndex &&
                failedBrowser.currentTimeline.toMediaItems().map { it.mediaId } == failedIds

            runPlaybackFailureRecovery(
                isCurrent = ::isCurrentFailure,
                onFailure = { Logger.e(it) { "Could not recover playback failure; pausing only the failed selection" } },
                pause = { failedBrowser.pause() },
            ) recover@{
                _errors.emit(error)
                if (!isCurrentFailure()) return@recover
                val audio = audioRepository.getAudioByPlaybackId(mediaId).first()
                val browser = failedBrowser
                if (!isCurrentFailure()) return@recover
                // Startup prepares only after readiness, in onQueueRestored. An error during normal
                // navigation must skip an unready source, not start another unbounded source wait.
                if (audio != null && isContentReady(audio)) {
                    failureWrites.apply(writeTicket, PlaybackFailure(
                        reason = classifyPlaybackFailure(error),
                        occurredAtMillis = System.currentTimeMillis(),
                    )).onFailure {
                        Logger.e(it) { "Could not persist playback failure; continuing navigation" }
                    }
                }
                if (!isCurrentFailure()) return@recover
                // The public queue can lag behind a native transition. Resolve exact native slots.
                val nativeIds = failedIds
                val nativeIndex = failedIndex
                val slots = audioRepository.getPlaybackSlots(nativeIds).first()
                if (!isCurrentFailure()) return@recover
                val failures = playbackFailureRepository.failures.first()
                if (!isCurrentFailure()) return@recover
                val next = failureTraversal.next(failureTicket, nativeIds.size, nativeIndex, playbackMode.value) { index ->
                    val candidate = slots[index]
                    candidate != null && candidate.available && isContentReady(candidate) && candidate.playbackId !in failures
                }
                if (next == null) {
                    browser.pause()
                } else {
                    // Do not begin a new traversal here: all consecutive failures share one budget.
                    browser.seekTo(next, 0)
                    browser.prepare()
                }
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        refreshDuration()
    }

    /**
     * 时长以"播放器实际解析出来的"为准。
     *
     * 网络媒体源首扫拿不到时长（要等标签提取完才知道），此时 MediaItem 里带的就是 0；如果让这个 0
     * 盖住 Media3 从容器里读出的真实时长，进度条的总时长会一直停在 00:00，缓冲进度也就无从对照。
     */
    private fun refreshDuration() {
        val browser = browserInstance ?: return
        val fromMetadata = browser.mediaMetadata.durationMs?.takeIf { it > 0L }
        val fromPlayer = browser.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        _currentDuration.value = fromMetadata ?: fromPlayer ?: 0L
    }

    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {

    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // 时长会在准备过程中被解析出来（网络源尤其如此）：时间线更新时重新读一次
        refreshDuration()
        failureTraversal.observeQueue(timeline.toMediaItems().map { it.mediaId })
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
            val resolved = audioRepository.getPlaybackSlots(ids).first()
            // Repository results need not follow Timeline order. Preserve duplicates and never
            // compress missing entries, which would silently move the current index to another song.
            val knownById = knownItems.associateBy { it.playbackId }
            val items = ids.mapIndexed { index, id ->
                resolved[index] ?: knownById[id] ?: return@launch
            }
            queueBridge.acceptPlatformSnapshot(generation, items, currentIndex)
        }
    }

    /** Publish the historical selection before native callbacks can mirror the default index 0. */
    private suspend fun applyHistoryQueue(state: QueueState) = browserQueueMutex.withLock {
        val browser = browserFuture.await()
        withContext(Dispatchers.Main) {
            recordedPosition.value = browser.restoreHistoryQueueSelection(state, startupHistory)
            // No prepare/play here: unavailable sources must remain displayable without opening media.
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
