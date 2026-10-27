package com.lalilu.lplayer.playback

import com.lalilu.lplayer.core.withQueuePlaybackRequest
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
import com.lalilu.lmedia.domain.repository.PlaybackFailureRepository
import com.lalilu.lmedia.domain.model.PlaybackFailure
import com.lalilu.lmedia.domain.model.PlaybackFailureReason
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.domain.repository.getPlaybackSlots
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lplayer.core.actualBufferedPosition
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
) : CoroutineScope,
    Player.Listener,
    Playback,
    HistoryPositionProvider,
    Runnable,
    PlaybackHistory by history {

    private val logger = Logger.withTag("MPlayerPlayback")
    private val browserQueueMutex = Mutex()
    private val queueEditMutex = Mutex()
    private val failureWrites = PlaybackFailureWrites(playbackFailureRepository)
    override val coroutineContext: CoroutineContext = Dispatchers.IO
    private val sessionToken by lazy {
        SessionToken(context, ComponentName(context, MService::class.java))
    }

    private val queueBridge = PlatformQueueBridge(
        applyToPlatform = { state ->
            if (state.updateReason == QueueUpdateReason.HistoryRestore) applyHistoryQueue(state)
            else applyQueueState(state)
        },
        readPlatformAfterApply = { launch(Dispatchers.Main) { updateItems() } },
    )
    override val queue: PlayableQueue = queueBridge.queue
    private var browserInstance: MediaBrowser? = null
    private var historyRestorer: HistoryQueueRestorer? = null
    private var startupHistory: PlaybackHistory.HistorySnapshot? = null
    private val browserFuture by lazy {
        MediaBrowser
            .Builder(context, sessionToken)
            .setListener(object : MediaBrowser.Listener {
                override fun onExtrasChanged(controller: androidx.media3.session.MediaController, extras: Bundle) {
                    updatePending(extras)
                    _currentBufferedPosition.value = controller.actualBufferedPosition(queue.currentItem()?.playbackId)
                }
            })
            .buildAsync()
    }

    var pauseWhenCompletion: Boolean by mutableStateOf(false)
        private set

    // Protected mutable state flows
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    private val _wantsToPlay = MutableStateFlow(false)
    private val _errors = MutableSharedFlow<Throwable>()
    private val _currentDuration = MutableStateFlow(0L)
    private val _currentEngineItemKey = MutableStateFlow<String?>(null)
    private val recordedPosition = MutableStateFlow(0L)
    private val _currentBufferedPosition = MutableStateFlow(0L)
    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    private val _pendingNext = MutableStateFlow<LAudio?>(null)
    override val pendingNext: StateFlow<LAudio?> = _pendingNext.asStateFlow()
    private var pendingJob: Job? = null
    private var pendingNextId: String? = null
    private fun updatePending(extras: Bundle) {
        pauseWhenCompletion = extras.getBoolean("pauseAtEnd")
        val id = extras.getString("pendingNext")
        if (id == pendingNextId) return
        pendingNextId = id
        pendingJob?.cancel()
        if (id == null) { _pendingNext.value = null; return }
        pendingJob = launch { _pendingNext.value = audioRepository.getAudioByPlaybackId(id).first() }
    }

    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val wantsToPlay: StateFlow<Boolean> = _wantsToPlay.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentEngineItemKey: StateFlow<String?> = _currentEngineItemKey.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val durationResolver = PlaybackDurationResolver(
        currentItem = queue.currentItemFlow(),
        engineDuration = _currentDuration,
        engineItemKey = _currentEngineItemKey,
        scope = this,
    )
    override val stableDuration: StateFlow<Long> = durationResolver.stableDuration

    private val bufferedPositionResolver = BufferedPositionResolver(
        currentItem = queue.currentItemFlow(),
        engineBufferedPosition = _currentBufferedPosition,
        duration = stableDuration,
        dataSourceOf = { audio ->
            platformMediaSource.findEnabledSource(audio.mediaSourceName)?.dataSource
        },
        scope = this,
        preferEngine = true,
    )
    override val currentBufferedPosition: StateFlow<Long> = bufferedPositionResolver.bufferedPosition

    init {
        browserFuture.addListener(this@MPlayerPlayback, Dispatchers.Main.asExecutor())
    }

    /**
     * browser 连接成功回调
     */
    override fun run() {
        val browser = browserFuture.get() ?: return
        browserInstance = browser
        updatePending(browser.sessionExtras)
        browser.addListener(this@MPlayerPlayback)
        _isLoading.value = browser.playbackState == Player.STATE_BUFFERING
        launch(Dispatchers.Main) {
            while (isActive) {
                recordedPosition.value = browser.currentPosition.coerceAtLeast(0L)
                _currentBufferedPosition.value = browser.actualBufferedPosition(queue.currentItem()?.playbackId)
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
                preparationOwnedByPlayback = true,
            )
        }
        historyRestorer = restorer
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

    override suspend fun play() = runWithBrowser { play() }
    override suspend fun pause() = runWithBrowser { pause() }
    override suspend fun togglePlayPause() = runWithBrowser { if (playWhenReady) pause() else play() }
    override suspend fun stop() = runWithBrowser { stop() }
    override val canSkipNext: Flow<Boolean> = queue.expandedItems.map { it.list.isNotEmpty() }
    override val canSkipPrevious: Flow<Boolean> = canSkipNext
    override suspend fun seekTo(positionMs: Long) = runWithBrowser {
        seekTo(positionMs)
        recordedPosition.value = currentPosition.coerceAtLeast(0L)
    }

    override suspend fun skipTo(index: Int, start: Boolean) = queueEditMutex.withLock {
        if (index !in queue.stateSnapshot().list.indices || index == queue.stateSnapshot().index) return@withLock
        selectTrack(queue.stateSnapshot().list[index], start)
    }

    override suspend fun skipToNext() = runWithBrowser {
        pendingUserSelection = null
        sendCustomCommand(CustomCommand.SeekToNext.toSessionCommand(), Bundle.EMPTY).await()
    }
    override suspend fun skipToPrevious() = runWithBrowser {
        pendingUserSelection = null
        sendCustomCommand(CustomCommand.SeekToPrevious.toSessionCommand(), Bundle.EMPTY).await()
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
            val selected = if (startIndex in playlist.indices) startIndex else
                playlist.indexOfFirst { it.mediaKey == queue.currentItem()?.mediaKey }.coerceAtLeast(0)
            queueBridge.editAndRun(block = { replaceAll(playlist, selected) }, apply = { applySelection(it, start) }) {}
        }
    }

    override suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) = editQueueWithReceipt(block) {}

    override suspend fun editQueueWithReceipt(block: QueueUpdateRequest.() -> Unit, onApplied: (QueueState) -> Unit) = queueEditMutex.withLock {
        val before = queue.stateSnapshot()
        var replacing = false
        queueBridge.editAndRun(block = {
            block()
            replacing = replacesAll
            val proposed = build(QueueUpdateReason.Inner)
            if (proposed.selectionRevision == before.selectionRevision &&
                proposed.list.none { it.mediaKey == before.currentItem()?.mediaKey }) {
                val successor = if (replacing) null else before.list.drop(before.index + 1)
                    .firstOrNull { old -> proposed.list.any { it.mediaKey == old.mediaKey } }
                switchTo(successor?.let { next -> proposed.list.indexOfFirst { it.mediaKey == next.mediaKey } } ?: 0)
            }
        }, apply = { selected ->
            if (replacing) applySelection(selected, start = false) else applyQueueState(selected)
        }) {}
        onApplied(queue.stateSnapshot())
    }

    override suspend fun playAudio(audio: LAudio) = queueEditMutex.withLock {
        if (queue.currentItem()?.mediaKey == audio.mediaKey) return@withLock
        selectTrack(audio, start = true)
    }

    override suspend fun restoreFailedQueueEdit(expected: QueueState, original: QueueState, position: Long): Boolean =
        queueEditMutex.withLock {
            val browser = browserInstance ?: return@withLock false
            withContext(Dispatchers.Main) {
                restorePausedQueueIfOwned(
                    bridge = queueBridge, player = browser, expected = expected, original = original,
                    position = position, prepare = original.currentItem() != null,
                ).also { applied -> if (applied) recordedPosition.value = position.coerceAtLeast(0) }
            }
        }

    override suspend fun playNext(audio: LAudio) = runWithBrowser {
        sendCustomCommand(CustomCommand.PlayNext.toSessionCommand(), Bundle().apply {
            putString("playbackId", audio.playbackId)
        }).await()
    }

    /** Selection is one identity-based core command, not an optimistic replacement of the entire queue. */
    private var pendingUserSelection: String? = null

    private suspend fun selectTrack(audio: LAudio, start: Boolean) = runWithBrowser {
        pendingUserSelection = audio.playbackId
        try {
            val result = sendCustomCommand(CustomCommand.SelectTrack.toSessionCommand(), Bundle().apply {
                putString("playbackId", audio.playbackId)
                putBoolean("start", start)
            }).await()
            check(result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) { "Selection rejected: ${result.resultCode}" }
        } catch (error: Exception) {
            if (pendingUserSelection == audio.playbackId) pendingUserSelection = null
            throw error
        }
    }

    private suspend fun applySelection(selected: QueueState, start: Boolean) {
        runWithBrowser {
            pendingUserSelection = null
            val items = selected.list.mapIndexed { index, audio ->
                audio.toMediaItem().let { if (index == 0) it.withQueuePlaybackRequest(start) else it }
            }
            setMediaItems(items, selected.index, androidx.media3.common.C.TIME_UNSET)
        }
    }

    override suspend fun setPlaybackMode(mode: PlaybackMode) {
        val requested = when(mode) {
            PlaybackMode.SEQUENTIAL -> PlayMode.Sequential
            PlaybackMode.LOOP -> PlayMode.ListRecycle
            PlaybackMode.SINGLE_LOOP -> PlayMode.RepeatOne
            PlaybackMode.SHUFFLE -> PlayMode.Shuffle
        }.name
        runWithBrowser(claimHistory = false) {
            sendCustomCommand(CustomCommand.SetMode.toSessionCommand(),Bundle().apply { putString("mode",requested) }).await()
        }
    }

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        pauseWhenCompletion = !cancel
        runWithBrowser { sendCustomCommand(CustomCommand.PauseAtEnd.toSessionCommand(), Bundle().apply { putBoolean("enabled", !cancel) }).await() }
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
                // 恢复队列和定位意图；服务按缓存优先策略异步准备内容。
                browser.playWhenReady = false
                browser.setMediaItems(mediaIds, restored.index, snapshot.position)
                recordedPosition.value = snapshot.position.coerceAtLeast(0L)
            }
        }

        runWithBrowser(claimHistory = false) {
            prepare()
            playWhenReady = LPlayerKV.autoPlayWhenRestart.value
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

    override fun onEvents(player: Player, events: Player.Events) {
        _wantsToPlay.value = player.playWhenReady
        _isLoading.value = player.playbackState == Player.STATE_BUFFERING
        // 缓存通知在暂停时也会更新；按媒体身份读取会话中的真实范围。
        _currentBufferedPosition.value = (player as? MediaBrowser)?.actualBufferedPosition(queue.currentItem()?.playbackId) ?: player.bufferedPosition.coerceAtLeast(0L)

        // 引擎的真实时长。只靠 onMediaMetadataChanged 会漏：缓存命中的曲目往往在时长还没解析出来
        // 时就发出了那一次元数据回调，此后不再补发，_currentDuration 于是永远停在 0，
        // 进度条只剩"未知时长"的短条（TIME_UNSET 是负数，被这里的 > 0 过滤掉）。
        player.duration.takeIf { it > 0L }?.let { engineDuration ->
            if (_currentDuration.value != engineDuration) {
                _currentDuration.value = engineDuration
            }
        }

        // 事件批里再对一次"引擎加载的是哪一首"，避免个别路径没有走到 onMediaItemTransition
        player.currentMediaItem?.mediaId?.let { _currentEngineItemKey.value = it }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // 引擎切到哪一首：时长归属判断的唯一可靠依据。
        // 新曲目的时长此刻还未知，先清零，等引擎报出来再填——避免上一首的值被当成新曲目的。
        _currentEngineItemKey.value = mediaItem?.mediaId
        _currentDuration.value = 0L
        updateItems()

    }

    override fun onPlayerError(error: PlaybackException) {
        // Failure traversal belongs to the service core. The client only exposes/persists the terminal error.
        val mediaId = browserInstance?.currentMediaItem?.mediaId
        val ticket = mediaId?.let { failureWrites.register(it) }
        launch {
            _errors.emit(error)
            if(ticket != null) failureWrites.apply(ticket, PlaybackFailure(
                reason = classifyPlaybackFailure(error), occurredAtMillis = System.currentTimeMillis(),
            ))
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        // 未知时不要写 0：缓存命中的曲目常常在时长解析出来之前就发出这一次回调，
        // 写 0 会把 onEvents 采到的真实时长抹掉，进度条于是永远是"未知"。
        updateEngineDuration(mediaMetadata.durationMs)
    }

    /**
     * 更新引擎时长。[metadataDurationMs] 是媒体元数据里带的时长（我们自己塞进去的）。
     *
     * 优先用引擎当前的真实时长：它总是属于引擎正在加载的那一首，而元数据可能来自
     * 还没补全标签的歌曲（值为 0）。两者都拿不到时保持原值，交给曲目切换去清零。
     */
    private fun updateEngineDuration(metadataDurationMs: Long?) {
        val duration = browserInstance?.duration?.takeIf { it > 0L }
            ?: metadataDurationMs?.takeIf { it > 0L }
            ?: return
        if (_currentDuration.value != duration) {
            _currentDuration.value = duration
        }
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
            val resolved = audioRepository.getPlaybackSlots(ids).first()
            // Repository results need not follow Timeline order. Preserve duplicates and never
            // compress missing entries, which would silently move the current index to another song.
            val knownById = knownItems.associateBy { it.playbackId }
            val items = ids.mapIndexed { index, id ->
                resolved[index] ?: knownById[id] ?: return@launch
            }
            withContext(Dispatchers.Main) {
                val picked = pendingUserSelection?.takeIf { it == items.getOrNull(currentIndex)?.playbackId }
                if (queueBridge.acceptPlatformSnapshot(generation, items, currentIndex, selectedByUser = picked != null) && picked != null) {
                    if (pendingUserSelection == picked) pendingUserSelection = null
                }
            }
        }
    }

    /** Publish the historical selection before native callbacks can mirror the default index 0. */
    private suspend fun applyHistoryQueue(state: QueueState) = browserQueueMutex.withLock {
        val browser = browserFuture.await()
        withContext(Dispatchers.Main) {
            recordedPosition.value = browser.restoreHistoryQueueSelection(state, startupHistory)
            // The core prepares asynchronously while keeping the restored pause intent.
        }
    }

    private suspend fun applyQueueState(state: QueueState) = browserQueueMutex.withLock {
        val browser = browserFuture.await()
        val mediaItems = state.list.map { it.toMediaItem() }
        withContext(Dispatchers.Main) {
            val ids = browser.currentTimeline.toMediaItems().map { it.mediaId }
            if (ids == mediaItems.map { it.mediaId } && browser.currentMediaItemIndex == state.index) {
                // Metadata refresh does not change queue intent or current position.
                browser.replaceMediaItems(0, browser.mediaItemCount, mediaItems)
            } else {
                val oldCurrent = browser.currentMediaItem?.mediaId
                val newIds = mediaItems.map { it.mediaId }
                val removedCurrent = oldCurrent != null && oldCurrent !in newIds && !state.currentPickedByUser
                val target = if (removedCurrent) {
                    val following = ids.drop(browser.currentMediaItemIndex + 1).firstOrNull { it in newIds }
                    following?.let(newIds::indexOf) ?: 0
                } else state.index.coerceAtLeast(0)
                browser.setMediaItems(mediaItems, target, androidx.media3.common.C.TIME_UNSET)
            }
        }
    }

    private suspend fun runWithBrowser(
        claimHistory: Boolean = true,
        block: suspend MediaBrowser.() -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val browser = browserFuture.await()
        if (claimHistory) historyRestorer?.claimPlaybackControl()
        withContext(Dispatchers.Main) {
            browser.block()
        }
    }
}

private fun Timeline.toMediaItems(): List<MediaItem> {
    return (0 until this.windowCount)
        .mapNotNull { this.getWindow(it, Timeline.Window()).mediaItem }
}
