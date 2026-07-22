package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Engine test double，记录所有调用供测试断言。
 *
 * 通过 [canHandleResult] 控制 [canHandle] 的返回值，
 * 通过 [trackedEvents] 记录每次操作调用以供 [events] 断言。
 *
 * 用法：
 * ```
 * val engine = FakeEngine(canHandleResult = true)
 * engine.load(MediaData.Url("http://test"), dummyAudio)
 * assertEquals(1, engine.loadCount)
 * assertEquals("load", engine.events.last())
 * ```
 */
class FakeEngine(
    val name: String = "FakeEngine",
    val canHandleResult: Boolean = true,
) : PlaybackEngine {

    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    override var onEvent: (suspend (PlaybackEngineEvent) -> Unit)? = null

    // ── 调用计数 ──
    var loadCount = 0
        private set
    var playCount = 0
        private set
    var pauseCount = 0
        private set
    var stopCount = 0
        private set
    var seekCount = 0
        private set
    var releaseCount = 0
        private set

    // ── 调用参数 ──
    var lastLoadedMediaData: MediaData? = null
        private set
    var lastLoadedAudio: LAudio? = null
        private set
    var lastSeekPosition: Long = 0L
        private set

    // ── 时序追踪 ──
    private val trackedEvents = mutableListOf<String>()
    val events: List<String> get() = trackedEvents.toList()

    // ── 可控状态 ──
    var stubPosition: Long = 0L
    var stubDuration: Long = 1000L
    var stubIsPlaying: Boolean = false

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean {
        trackedEvents.add("canHandle(${mediaData::class.simpleName})")
        return canHandleResult
    }

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        loadCount++
        lastLoadedMediaData = mediaData
        lastLoadedAudio = audio
        _state.value = PlaybackEngineState(
            isLoading = false,
            duration = stubDuration,
            position = 0L
        )
        trackedEvents.add("load")
    }

    override suspend fun play() {
        playCount++
        stubIsPlaying = true
        _state.update { it.copy(isPlaying = true) }
        trackedEvents.add("play")
    }

    override suspend fun pause() {
        pauseCount++
        stubIsPlaying = false
        _state.update { it.copy(isPlaying = false) }
        trackedEvents.add("pause")
    }

    override suspend fun stop() {
        stopCount++
        stubIsPlaying = false
        _state.value = PlaybackEngineState(duration = stubDuration)
        trackedEvents.add("stop")
    }

    override suspend fun seekTo(positionMs: Long) {
        seekCount++
        lastSeekPosition = positionMs
        _state.update { it.copy(position = positionMs) }
        trackedEvents.add("seekTo")
    }

    override fun currentPosition(): Long = stubPosition

    override suspend fun release() {
        releaseCount++
        _state.value = PlaybackEngineState.EMPTY
        stubIsPlaying = false
        trackedEvents.add("release")
    }

    /** 重置所有调用记录 */
    fun reset() {
        loadCount = 0; playCount = 0; pauseCount = 0
        stopCount = 0; seekCount = 0; releaseCount = 0
        lastLoadedMediaData = null; lastLoadedAudio = null
        lastSeekPosition = 0L; stubPosition = 0L; stubIsPlaying = false
        trackedEvents.clear()
        _state.value = PlaybackEngineState.EMPTY
    }
}
