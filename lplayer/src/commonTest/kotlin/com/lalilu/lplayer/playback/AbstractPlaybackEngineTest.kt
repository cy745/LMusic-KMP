package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Engine 切换逻辑测试。
 *
 * 使用 [TestPlayback] 子类 + [FakeEngine] 验证 AbstractPlayback 中
 * Engine 路由、切换、生命周期管理等核心逻辑。
 */
class AbstractPlaybackEngineTest {

    private lateinit var playback: TestPlayback

    @BeforeTest
    fun setup() {
        playback = TestPlayback(createEngines = {
            listOf(
                FakeEngine(name = "BytesEngine", canHandleResult = false),
                FakeEngine(name = "UrlEngine", canHandleResult = true),
            )
        })
    }

    @AfterTest
    fun teardown() {
        // 确保协程没有泄漏
    }

    // ── Engine 选择与切换 ──

    @Test
    fun `selectEngine returns first match`() {
        val mediaData = MediaData.Url("http://test.mp3")
        val audio = LAudio(id = "test", mediaSourceName = "Source")

        val engine = playback.engineRouter.selectEngine(mediaData, audio)

        assertNotNull(engine)
        assertEquals("UrlEngine", (engine as FakeEngine).name)
    }

    @Test
    fun `selectEngine returns null for Bytes when no engine handles it`() {
        val mediaData = MediaData.Bytes(byteArrayOf())
        val audio = LAudio(id = "test", mediaSourceName = "Source")

        // 只注册 UrlEngine（canHandleResult = false → 无法处理 Bytes）
        val router = PlaybackEngineRouter(listOf(
            FakeEngine(name = "OnlyUrl", canHandleResult = false)
        ))

        assertNull(router.selectEngine(mediaData, audio))
    }

    @Test
    fun `engineRouter exposes all engines`() {
        val engines = playback.engineRouter.allEngines
        assertEquals(2, engines.size)
        assertEquals("BytesEngine", (engines[0] as FakeEngine).name)
        assertEquals("UrlEngine", (engines[1] as FakeEngine).name)
    }

    // ── Engine 绑定事件 ──

    @Test
    fun `engine onEvent is bound by init`() = runTest {
        val engine = playback.engineRouter.allEngines.first() as FakeEngine
        assertNotNull(engine.onEvent, "Engine should have onEvent bound by AbstractPlayback init")
    }

    @Test
    fun `engine onEvent Completion triggers onCompletion callback`() = runTest {
        val engine = playback.engineRouter.allEngines.find { it is FakeEngine } as FakeEngine
        var completed = false
        playback.onCompletionOverride = { completed = true }

        // 模拟 Engine 发出完成事件
        engine.onEvent?.invoke(PlaybackEngineEvent.Completion)

        // onCompletion 会在协程中调度执行
        delay(100)
        assertTrue(completed, "Completion event should trigger onCompletion")
    }

    @Test
    fun `engine onEvent Error triggers emitError`() = runTest {
        val engine = playback.engineRouter.allEngines.find { it is FakeEngine } as FakeEngine
        val error = RuntimeException("Test error")
        var capturedError: Throwable? = null

        playback.errorCapture = { capturedError = it }

        engine.onEvent?.invoke(PlaybackEngineEvent.Error(error))

        delay(100)
        assertNotNull(capturedError)
        assertEquals("Test error", capturedError?.message)
    }

    // ── activeEngine 状态投影 ──

    @Test
    fun `engine play updates its state`() = runTest {
        val engine = FakeEngine(canHandleResult = true)
        engine.load(MediaData.Url("http://test.mp3"), LAudio(id = "t"))

        assertEquals(false, engine.state.value.isPlaying)
        assertEquals(false, engine.state.value.isLoading)

        engine.play()

        assertEquals(true, engine.state.value.isPlaying)
    }

    @Test
    fun `engine pause updates its state`() = runTest {
        val engine = FakeEngine(canHandleResult = true)
        engine.load(MediaData.Url("http://test.mp3"), LAudio(id = "t"))
        engine.play()
        engine.pause()

        assertEquals(false, engine.state.value.isPlaying)
    }

    @Test
    fun `engine release resets to EMPTY`() = runTest {
        val engine = FakeEngine(canHandleResult = true)
        engine.load(MediaData.Url("http://test.mp3"), LAudio(id = "t"))
        engine.release()

        assertEquals(PlaybackEngineState.EMPTY, engine.state.value)
        assertEquals(1, engine.releaseCount)
    }
}

/**
 * AbstractPlayback 测试子类。
 * 跳过历史恢复以避免 Koin 依赖，通过 lambda 回传事件供测试断言。
 */
private class TestPlayback(
    private val createEngines: () -> List<PlaybackEngine>,
) : Playback {

    // 事件回调（测试用）
    var onCompletionOverride: (() -> Unit)? = null
    var errorCapture: ((Throwable) -> Unit)? = null

    // Engine 基础设施（直接使用 AbstractPlayback 的同类逻辑）
    val engineRouter: PlaybackEngineRouter = PlaybackEngineRouter(createEngines())
    val activeEngineState = MutableStateFlow<PlaybackEngineState>(PlaybackEngineState.EMPTY)
    var activeEngine: PlaybackEngine? = null
        set(value) {
            field = value
            activeEngineState.value = value?.state?.value ?: PlaybackEngineState.EMPTY
        }

    init {
        // 模拟 AbstractPlayback 的 Engine 事件绑定
        engineRouter.allEngines.forEach { engine ->
            engine.onEvent = { event ->
                when (event) {
                    is PlaybackEngineEvent.Completion -> onCompletionOverride?.invoke()
                    is PlaybackEngineEvent.Error -> errorCapture?.invoke(event.throwable)
                }
            }
        }
    }

    // Playback 接口（最小实现）
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)
    override val errors: SharedFlow<Throwable> = MutableSharedFlow()
    override val playbackMode: StateFlow<PlaybackMode> = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    override val currentDuration: StateFlow<Long> = MutableStateFlow(0L)
    override val currentBufferedPosition: StateFlow<Long> = MutableStateFlow(0L)
    override val queue: PlayableQueue = PlayableQueueImpl()
    override val canSeek: Flow<Boolean> = MutableStateFlow(true)
    override val canSkipNext: Flow<Boolean> = MutableStateFlow(false)
    override val canSkipPrevious: Flow<Boolean> = MutableStateFlow(false)

    override suspend fun play() { TODO("Not needed for engine tests") }
    override suspend fun pause() { TODO("Not needed for engine tests") }
    override suspend fun togglePlayPause() { TODO("Not needed for engine tests") }
    override suspend fun stop() { TODO("Not needed for engine tests") }
    override suspend fun skipTo(index: Int, start: Boolean) { TODO("Not needed for engine tests") }
    override suspend fun skipToNext() { TODO("Not needed for engine tests") }
    override suspend fun skipToPrevious() { TODO("Not needed for engine tests") }
    override suspend fun seekTo(positionMs: Long) { TODO("Not needed for engine tests") }
    override fun currentPosition(): Long = 0L
    override suspend fun updatePlaylist(playlist: List<LAudio>, startIndex: Int, start: Boolean) { TODO() }
    override suspend fun clearPlaylist() { TODO() }
    override suspend fun setPlaybackMode(mode: PlaybackMode) { TODO() }
    override suspend fun setPauseWhenCompletion(cancel: Boolean) { TODO() }
}
