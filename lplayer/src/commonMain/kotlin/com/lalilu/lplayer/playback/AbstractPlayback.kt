package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.lplayer.action.launchPlayerAction
import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.MediaSourceBindingRepository
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import com.lalilu.lmedia.domain.source.resolveMediaData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.mp.KoinPlatform

/**
 * Abstract base implementation of Playback interface.
 * Provides common functionality for all platform implementations,
 * including automatic history recovery and recording via [PlaybackHistory] delegation.
 */
@Suppress("PropertyName")
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AbstractPlayback(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob()),
    private val history: PlaybackHistory,
    protected open val audioRepository: AudioRepository
) : Playback,
    CoroutineScope by coroutineScope,
    PlaybackHistory by history {

    // Protected mutable state flows — 必须在 init 块之前声明
    // 确保 init 中的 startRecording() / restoreFromHistory() 能安全访问所有属性
    protected val _isPlaying = MutableStateFlow(false)
    protected val _errors = MutableSharedFlow<Throwable>()
    protected val _currentDuration = MutableStateFlow(0L)
    protected val _currentBufferedPosition = MutableStateFlow(0L)
    protected val _playbackMode = MutableStateFlow(history.historyStorage.savedPlaybackMode())
    protected var _pauseWhenCompletion: Boolean = false
    private val logger = Logger.withTag("AbstractPlayback")
    private val queueEditMutex = Mutex()
    private var historyStarted = false

    // ── Engine 基础设施 ──

    private val _platformMediaSource: PlatformMediaSource by lazy {
        KoinPlatform.getKoin().get<PlatformMediaSource>()
    }
    private val mediaSourceBindingRepository: MediaSourceBindingRepository by lazy {
        KoinPlatform.getKoin().get<MediaSourceBindingRepository>()
    }

    /** 平台媒体源聚合体，子类可通过 override（如 by inject()）提供特定实现。 */
    protected open val platformMediaSource: PlatformMediaSource
        get() = _platformMediaSource

    /** 返回当前平台支持的 Engine 列表。注册顺序即匹配优先级。 */
    protected abstract fun createEngines(): List<PlaybackEngine>

    /** 链式匹配路由器，按 [createEngines] 注册顺序优先匹配。 */
    protected val engineRouter: PlaybackEngineRouter by lazy {
        PlaybackEngineRouter(createEngines())
    }

    private val _activeEngine = MutableStateFlow<PlaybackEngine?>(null)

    /** 当前活跃的 Engine。切换时自动 release 旧的并 load 新的。 */
    protected var activeEngine: PlaybackEngine?
        get() = _activeEngine.value
        set(value) {
            _activeEngine.value = value
        }

    /** activeEngine 连续状态投影，供子类或对 Engine 状态做额外处理 */
    protected val activeEngineState: StateFlow<PlaybackEngineState> = _activeEngine
        .flatMapLatest { it?.state ?: flowOf(PlaybackEngineState.EMPTY) }
        .stateIn(coroutineScope, SharingStarted.Eagerly, PlaybackEngineState.EMPTY)

    // Public state flows
    override val queue: PlayableQueue = PlayableQueueImpl()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val errors: SharedFlow<Throwable> = _errors.asSharedFlow()
    override val currentDuration: StateFlow<Long> = _currentDuration.asStateFlow()
    override val currentBufferedPosition: StateFlow<Long> = _currentBufferedPosition.asStateFlow()
    override val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    init {
        // 数据源刷新完成后只替换队列内的歌曲描述，不重建播放引擎或改变 position。
        startQueueMetadataRefresh(queue, audioRepository)

        // ── Engine 事件绑定 ──
        // 给每个 Engine 绑定 onEvent 回调，将离散事件转为 Playback 方法调用
        engineRouter.allEngines.forEach { engine ->
            engine.onEvent = { event ->
                when (event) {
                    is PlaybackEngineEvent.Completion -> {
                        launchPlayerAction { onCompletion() }
                    }

                    is PlaybackEngineEvent.Error -> {
                        emitError(event.throwable)
                    }
                }
            }
        }

        // 监听 activeEngine 状态，同步到 Playback 的标准 StateFlow
        _activeEngine
            .flatMapLatest { it?.state ?: flowOf(PlaybackEngineState.EMPTY) }
            .onEach { state ->
                _isPlaying.value = state.isPlaying
                _currentDuration.value = state.duration
                _currentBufferedPosition.value = state.bufferedPosition
            }
            .launchIn(coroutineScope)
    }

    private var historyRestorer: HistoryQueueRestorer? = null

    protected suspend fun claimHistoryPlaybackControl() {
        historyRestorer?.claimPlaybackControl()
    }

    /** Called by the concrete platform only after its native player has been initialized. */
    protected fun startHistoryPlayback() {
        if (historyStarted) return
        historyStarted = true
        val snapshot = restoreFromHistory()
        val restorer = snapshot?.let {
            HistoryQueueRestorer(it, audioRepository,
                mediaSourceBindingRepository.observeHistoryRestoreSettled(it.sourceNames.getOrNull(it.index)))
        }
        historyRestorer = restorer
        restorer?.start(this, queue, ::onQueueRestored)
        startRecording(this, restorer?.state)
    }

    override suspend fun onQueueRestored(snapshot: PlaybackHistory.HistorySnapshot) {
        val state = queue.stateSnapshot()
        val resolved = snapshot.resolveQueue(state.list)
        val current = state.currentItem() ?: throw CancellationException("History selection disappeared")
        if (resolved.currentIndex != state.index || resolved.items.getOrNull(resolved.currentIndex)?.mediaKey != current.mediaKey) {
            throw CancellationException("History selection was replaced")
        }
        restoreLoadedItem(current, snapshot.position, history.historyStorage.autoPlayOnRestore())
    }

    /** Must return only after native loading and positioning succeed; never edit the restored queue. */
    protected open suspend fun restoreLoadedItem(audio: LAudio, position: Long, start: Boolean) {
        skipTo(queue.stateSnapshot().index, false)
        seekTo(position)
        if (start) play()
    }

    /**
     * 将 id 列表解析为 [LAudio] 列表。
     * 默认使用 [audioRepository] 实现；平台可覆盖以提供自定义逻辑。
     */
    protected open suspend fun resolveMedia(ids: List<String>): List<LAudio> {
        return audioRepository.getAudios(ids).first()
    }

    /**
     * 通过 [platformMediaSource] 解析音频媒体数据。
     * 消除三平台 [playItem] 中反复出现的 MediaSource 查找 + getMedia 调用。
     */
    protected suspend fun resolveMediaData(audio: LAudio): MediaData {
        return platformMediaSource.resolveMediaData(audio)
    }

    /**
     * Engine 切换完成后的 hook。子类可 override 以补充逻辑（如 JVM 的 dataTracker 回调）。
     */
    protected open suspend fun onEngineSwitched(engine: PlaybackEngine, item: LAudio) {}

    /**
     * 当播放完成时调用
     */
    protected suspend fun onCompletion() {
        logger.i { "onCompletion, _pauseWhenCompletion: $_pauseWhenCompletion" }
        if (_pauseWhenCompletion) pause() else skipToNext()
    }

    // Default implementations
    override suspend fun togglePlayPause() {
        logger.i { "togglePlayPause, _isPlaying: ${_isPlaying.value}" }
        if (_isPlaying.value) pause() else play()
    }

    override suspend fun skipToNext() {
        logger.i { "skipToNext()" }
        val currentState = queue.stateSnapshot()
        val flattened = currentState.list
        if (flattened.isEmpty()) return

        val nextIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> currentState.index
            PlaybackMode.SHUFFLE -> {
                prepareSurpriseNext()
                SurpriseQueueOrder.nextIndex(flattened.size, currentState.index)
            }

            PlaybackMode.LOOP -> (currentState.index + 1) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (currentState.index < flattened.size - 1) {
                    currentState.index + 1
                } else {
                    -1 // End of playlist
                }
            }
        }

        if (nextIndex != -1) {
            skipTo(index = nextIndex, start = true)
        } else {
            logger.i { "Already at the end of the playlist" }
        }
    }

    override suspend fun skipToPrevious() {
        logger.i { "skipToPrevious()" }
        val currentState = queue.stateSnapshot()
        val flattened = currentState.list
        if (flattened.isEmpty()) return

        val previousIndex = when (_playbackMode.value) {
            PlaybackMode.SINGLE_LOOP -> currentState.index
            PlaybackMode.SHUFFLE -> {
                SurpriseQueueOrder.previousIndex(flattened.size, currentState.index)
            }

            PlaybackMode.LOOP -> (currentState.index - 1 + flattened.size) % flattened.size
            PlaybackMode.SEQUENTIAL -> {
                if (currentState.index > 0) {
                    currentState.index - 1
                } else {
                    -1 // Beginning of playlist
                }
            }
        }

        if (previousIndex != -1) {
            skipTo(index = previousIndex, true)
        }
    }

    override suspend fun updatePlaylist(playlist: List<LAudio>, startIndex: Int, start: Boolean) {
        logger.i {
            "Updating playlist size: ${playlist.size}, startIndex: $startIndex, start: $start\n" +
                    playlist.joinToString(separator = "\n") { "(${it.id}) ${it.title}" }
        }
        if (playlist.isEmpty()) {
            clearPlaylist()
            return
        }
        queue.update { replaceAll(items = playlist, index = startIndex) }

        skipTo(queue.stateSnapshot().index, start)
    }

    override suspend fun editQueue(block: QueueUpdateRequest.() -> Unit) = queueEditMutex.withLock {
        super<Playback>.editQueue(block)
    }

    override suspend fun playAudio(audio: LAudio) = queueEditMutex.withLock {
        super<Playback>.playAudio(audio)
    }

    override suspend fun clearPlaylist() {
        logger.i { "Clearing playlist $this" }
        editQueue { clear() }
    }

    override suspend fun setPlaybackMode(mode: PlaybackMode) {
        logger.i { "Setting playback mode: $mode" }
        if (_playbackMode.value == mode) return

        _playbackMode.value = mode
        if (mode == PlaybackMode.SHUFFLE) prepareSurpriseNext()
    }

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        _pauseWhenCompletion = !cancel
    }

    protected suspend fun prepareSurpriseNext() {
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            queue.update { prepareSurpriseNext() }
        }
    }

    protected fun emitError(error: Throwable) {
        launch { _errors.emit(error) }
    }
}
