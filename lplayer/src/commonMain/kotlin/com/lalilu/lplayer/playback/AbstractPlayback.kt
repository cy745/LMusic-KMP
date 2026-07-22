package com.lalilu.lplayer.playback

import co.touchlab.kermit.Logger
import com.lalilu.common.ext.io
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.source.MediaData
import com.lalilu.lmedia.domain.source.PlatformMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random
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
    protected val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    protected var _pauseWhenCompletion: Boolean = false
    protected var _shuffledIndices: List<Int> = emptyList()
    protected var _currentIndexInShuffled: Int = 0
    private val logger = Logger.withTag("AbstractPlayback")

    // ── Engine 基础设施 ──

    private val _platformMediaSource: PlatformMediaSource by lazy {
        KoinPlatform.getKoin().get<PlatformMediaSource>()
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
        set(value) { _activeEngine.value = value }

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
        // 自动恢复历史队列
        val snapshot = restoreFromHistory()
        if (snapshot != null) {
            launch {
                val items = resolveMedia(snapshot.ids)
                queue.update { replaceAll(items, snapshot.index) }
                onQueueRestored(snapshot)
            }
        }

        // 自动录制播放状态 — 此时所有属性均已初始化
        startRecording(this)

        // ── Engine 事件绑定 ──
        // 给每个 Engine 绑定 onEvent 回调，将离散事件转为 Playback 方法调用
        engineRouter.allEngines.forEach { engine ->
            engine.onEvent = { event ->
                when (event) {
                    is PlaybackEngineEvent.Completion -> {
                        launch { onCompletion() }
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
        val source = platformMediaSource.sources
            .firstOrNull { audio.mediaSourceName == it.name }
            ?: throw Exception("MediaSource '${audio.mediaSourceName}' not found for ${audio.id}")
        return source.dataSource.getMedia(audio)
            ?: throw Exception("MediaData unavailable for ${audio.id}")
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
                if (_shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                _currentIndexInShuffled = (_currentIndexInShuffled + 1) % _shuffledIndices.size
                _shuffledIndices[_currentIndexInShuffled]
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
                if (_shuffledIndices.isEmpty()) {
                    updateShuffledIndices()
                }
                _currentIndexInShuffled = (_currentIndexInShuffled - 1 + _shuffledIndices.size) % _shuffledIndices.size
                _shuffledIndices[_currentIndexInShuffled]
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
        queue.update { replaceAll(items = playlist, index = startIndex) }

        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            updateShuffledIndices()
        }
        skipTo(startIndex, start)
    }

    override suspend fun clearPlaylist() {
        logger.i { "Clearing playlist $this" }
        queue.update { clear() }
        _shuffledIndices = emptyList()
        _currentIndexInShuffled = 0
    }

    override suspend fun setPlaybackMode(mode: PlaybackMode) {
        logger.i { "Setting playback mode: $mode" }
        if (_playbackMode.value == mode) return

        val oldMode = _playbackMode.value
        _playbackMode.value = mode

        if (oldMode == PlaybackMode.SHUFFLE || mode == PlaybackMode.SHUFFLE) {
            if (mode == PlaybackMode.SHUFFLE) {
                updateShuffledIndices()
                val currentIndex = queue.stateSnapshot().index
                _currentIndexInShuffled = _shuffledIndices.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
            } else {
                if (oldMode == PlaybackMode.SHUFFLE) {
                    queue.update { switchTo(_shuffledIndices.getOrNull(_currentIndexInShuffled) ?: 0) }
                }
            }
        }
    }

    override suspend fun setPauseWhenCompletion(cancel: Boolean) {
        _pauseWhenCompletion = !cancel
    }

    private fun updateShuffledIndices() {
        val currentState = queue.stateSnapshot()
        val size = currentState.list.size
        _shuffledIndices = (0 until size).toList().shuffled(Random.Default)
        _currentIndexInShuffled = _shuffledIndices.indexOf(currentState.index).takeIf { it >= 0 } ?: 0
    }

    protected fun emitError(error: Throwable) {
        launch { _errors.emit(error) }
    }
}
