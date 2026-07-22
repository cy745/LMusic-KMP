# PlaybackEngine 重构任务追踪

> 目标：引入 PlaybackEngine 架构，将 iOS 端三个播放技术（AVPlayer / AVAudioPlayer / 未来 MusicKit）封装为独立的 Engine，通过 EngineRouter 链式匹配消除 if/else 模式，统一跨平台的 skipTo()/resolveMediaData() 骨架代码。

---

## 总体设计

```
commonMain:
  PlaybackEngine (interface) + PlaybackEngineState + PlaybackEngineEvent
  PlaybackEngineRouter
  AbstractPlayback (enhanced: activeEngine mgmt, resolveMediaData, unified skipTo)

iosMain:
  AVPlayerEngine     ← extracted from AVPlayerPlayback (handles MediaData.Url)
  AVAudioPlayerEngine ← extracted from AVPlayerPlayback (handles MediaData.Bytes)
  MusicKitEngine     ← placeholder for future (handles MusicKitSource songs)
  AVPlayerPlayback   ← refactored: createEngines() + 100-line shell

commonTest:
  FakeEngine          ← test double for Engine contract
  RouterTest          ← chain matching priority
  AbstractPlaybackTest ← Engine switching lifecycle

iosTest:
  AVPlayerEngineContractTest
  AVAudioPlayerEngineContractTest
```

---

## 阶段划分

### 阶段 0：Engine 抽象定义（commonMain）

**预期**：纯新增，不修改任何已有代码，不改变任何行为。

**文件变更**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `lplayer/src/commonMain/.../playback/PlaybackEngine.kt` | 新增 | Engine 接口 + State + Event |
| `lplayer/src/commonMain/.../playback/PlaybackEngineRouter.kt` | 新增 | 链式匹配 Router |

**PlaybackEngine 接口**：

```kotlin
interface PlaybackEngine {
    val state: StateFlow<PlaybackEngineState>

    fun canHandle(mediaData: MediaData, audio: LAudio): Boolean
    suspend fun load(mediaData: MediaData, audio: LAudio)
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(positionMs: Long)
    fun currentPosition(): Long
    suspend fun release()

    // 由 AbstractPlayback 在初始化时绑定
    protected var onEvent: (suspend (PlaybackEngineEvent) -> Unit)?
}
```

```
[ ] PlaybackEngine.kt — interface + state + event
[ ] PlaybackEngineRouter.kt — selectEngine() + NoEngineFoundException
```

**自动化验收**：

```
☐ commonMain 编译通过
☐ Router 注册 3 个 FakeEngine，按注册顺序匹配
☐ 无匹配 Engine 时返回 null
```

---

### 阶段 1：AbstractPlayback 增强（commonMain + 三平台）

**预期**：将三平台重复的 `skipTo()` / `resolveMediaData()` 骨架统一到 AbstractPlayback。

**文件变更**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `AbstractPlayback.kt` | 修改 | 新增 `createEngines()`, `activeEngine`, `resolveMediaData()`, `skipTo()` |
| `AVPlayerPlayback.kt` | 修改 | 移除此前的 `playItem()` 和 `skipTo()`，继承父类 |
| `VLCPlayback.kt` | 修改 | 同上，保留 `onEngineSwitched()` hook 调用 dataTracker |
| `AudioPlayback.kt` | 修改 | 同上，继承父类 |
| `AVPlayerEngine.kt` | 新增 | 空壳实现（阶段性构造，等阶段 2 填充） |
| `AVAudioPlayerEngine.kt` | 新增 | 空壳实现 |
| `VlcEngine.kt` | 新增 | 空壳实现 |
| `AudioElementEngine.kt` | 新增 | 空壳实现 |

**AbstractPlayback 新增内容**：

```kotlin
abstract class AbstractPlayback(...) {
    // Engine 管理
    protected abstract fun createEngines(): List<PlaybackEngine>
    private val router = PlaybackEngineRouter(createEngines())
    private val _activeEngine = MutableStateFlow<PlaybackEngine?>(null)
    protected var activeEngine: PlaybackEngine?
        get() = _activeEngine.value
        private set(value) { _activeEngine.value = value }

    // 统一媒体数据解析
    protected suspend fun resolveMediaData(audio: LAudio): MediaData

    // 统一 skipTo
    override suspend fun skipTo(index: Int, start: Boolean)

    // 子类 hook
    protected open suspend fun onEngineSwitched(engine: PlaybackEngine, item: LAudio) {}
}
```

**特别注意**：JVM 的 `VLCPlayback.skipTo()` 执行了 `dataTracker.onMediaItemTransition()`，该逻辑通过 `onEngineSwitched()` hook 保留，不丢失。

```
[ ] AbstractPlayback.kt — Engine 管理字段 + resolveMediaData()
[ ] AbstractPlayback.kt — 统一 skipTo() 实现
[ ] AbstractPlayback.kt — onEngineSwitched() hook
[ ] iOS: AVPlayerPlayback.kt — 移除 playItem/skipTo，实现 createEngines()
[ ] JVM: VLCPlayback.kt — 移除 playItem/skipTo，实现 createEngines() + hook
[ ] Web: AudioPlayback.kt — 移除 playItem/skipTo，实现 createEngines()
```

**自动化验收**：

```
☐ commonMain 编译通过
☐ iOS/JVM/Web 全部编译通过
☐ FakeEngine 测试验证 skipTo 调用 Engine.load() + Engine.play()
☐ FakeEngine 测试验证切换 Engine 时旧 Engine.release() 被调用
☐ FakeEngine 测试验证 resolveMediaData 使用 LAudio.mediaSourceName 查找 PlatformMediaSource
```

---

### 阶段 2：iOS Engine 提取（iosMain）

**预期**：将 AVPlayer 和 AVAudioPlayer 从 AVPlayerPlayback 中提取为独立的 Engine 类，保持行为完全一致。

**文件变更**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `AVPlayerEngine.kt` | 重写（从空壳→完整实现） | 内部持有 AVPlayer 单例，处理 MediaData.Url |
| `AVAudioPlayerEngine.kt` | 重写（从空壳→完整实现） | 每次 load 创建新 AVAudioPlayer，处理 MediaData.Bytes |
| `AVPlayerPlayback.kt` | 重写 | 精简为 Engine 宿主容器 + 平台基础设施绑定 |
| `AVPlayerEngine.kt`（helper）| 保留 | AVPlayerItemEventObserver / AVPlayerPositionObserver 保持独立 |

**AVPlayerEngine**：

```kotlin
class AVPlayerEngine : PlaybackEngine {
    private val avPlayer = AVPlayer()           // 复用，不销毁
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean =
        mediaData is MediaData.Url

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        // 1. 清理旧的 AVPlayerItem 观察者
        // 2. 创建新 AVPlayerItem
        // 3. KVO "status" → state.isLoading = false / state.duration
        // 4. addPeriodicTimeObserver → state.position
        // 5. AVPlayerItemDidPlayToEndTimeNotification → onEvent(Completion)
        // 6. replaceCurrentItemWithPlayerItem
    }

    override suspend fun play() { avPlayer.play() }
    override suspend fun pause() { avPlayer.pause() }
    override suspend fun seekTo(positionMs: Long) { avPlayer.seekToTime(...) }
    override fun currentPosition(): Long { ... }
    override suspend fun release() { avPlayer.pause(); avPlayer.replaceCurrentItemWithPlayerItem(null) }
}
```

**AVAudioPlayerEngine**：

```kotlin
class AVAudioPlayerEngine : PlaybackEngine {
    private var currentPlayer: AVAudioPlayer? = null   // 每次 load 新建
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)

    override fun canHandle(...): Boolean = mediaData is MediaData.Bytes

    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        release()
        // 1. 从 Bytes 创建 NSData
        // 2. 创建 AVAudioPlayer(data:)
        // 3. setDelegate → audioPlayerDidFinishPlaying → onEvent(Completion)
        // 4. state.duration = player.duration
    }

    override suspend fun play() { currentPlayer?.play() }
    override suspend fun pause() { currentPlayer?.pause() }
    override suspend fun seekTo(positionMs: Long) { currentPlayer?.currentTime = positionMs / 1000.0 }
    override fun currentPosition(): Long = (currentPlayer?.currentTime?.times(1000))?.toLong() ?: 0L
    override suspend fun release() { currentPlayer?.stop(); currentPlayer = null; _state.value = EMPTY }
}
```

**AVPlayerPlayback（重构后）**：

```kotlin
@Single(binds = [Playback::class])
class AVPlayerPlayback(...) : AbstractPlayback(...), KoinComponent {
    private val volumeFadeHelper = VolumeFadeHelper(...)

    override fun createEngines(): List<PlaybackEngine> = listOf(
        AVAudioPlayerEngine(/* onEvent from init */),
        AVPlayerEngine(/* onEvent from init */),
    )

    init {
        NowPlayingInfoNotification.bindPlayback(this)
        RemoteCommandHandler.bindPlayback(this)
        AudioSessionHelper.bindPlayback(this)
        // 给每个 Engine 绑定 onEvent 回调
        router.allEngines.forEach { engine -> engine.onEvent = { event ->
            when (event) {
                is PlaybackEngineEvent.Completion -> onCompletion()
                is PlaybackEngineEvent.Error -> emitError(event.throwable)
            }
        }}
        // 监听 activeEngine 状态
        _activeEngine.flatMapLatest { it?.state ?: flowOf(EMPTY) }
            .onEach { s -> _isPlaying.value = s.isPlaying; _currentDuration.value = s.duration }
            .launchIn(coroutineScope)
    }

    override suspend fun play() {
        if (activeEngine?.state?.value?.hasValidMedia == true) {
            volumeFadeHelper.play()
            activeEngine?.play()
        } else {
            val current = queue.currentItem() ?: throw Exception("No media to play")
            skipTo(queue.stateSnapshot().index, true)
        }
    }
    // pause/stop/seekTo 直接交给 activeEngine → 继承父类 skipTo 统一切换
}
```

```
[ ] AVPlayerEngine.kt — 完整实现（load/play/pause/seek/release + KVO + position observer）
[ ] AVAudioPlayerEngine.kt — 完整实现（load/play/pause/seek/release + delegate）
[ ] AVPlayerPlayback.kt — 重写为 Engine 容器
```

**自动化验收（commonTest + iosTest）**：

```
☐ FakeEngine 测试：Router 按 [AVAudioPlayerEngine, AVPlayerEngine] 顺序匹配
    - Bytes → 选中 AVAudioPlayerEngine
    - Url → 选中 AVPlayerEngine
    - Url（MusicKitSource 歌曲）→ 选中 AVPlayerEngine（当前行为保持）
☐ AVPlayerEngineContractTest（iosSimulatorArm64Test）：
    - canHandle(Url) → true
    - canHandle(Bytes) → false
    - load(Url) → state.isLoading = false, duration > 0
    - release() → state = EMPTY
☐ AVAudioPlayerEngineContractTest（iosSimulatorArm64Test）：
    - canHandle(Bytes) → true
    - canHandle(Url) → false
    - load(Bytes) → state.isLoading = false
    - release() → currentPlayer = null
```

---

### 阶段 3：单测基础设施（commonTest + iosTest）

**预期**：为 Engine 架构建立完整的单测覆盖，确保后续新增 Engine 或修改逻辑时能快速回归。

**文件变更**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `FakeEngine.kt` | 新增 | commonTest 下的 Engine test double |
| `PlaybackEngineRouterTest.kt` | 新增 | Router 匹配逻辑测试 |
| `AbstractPlaybackEngineTest.kt` | 新增 | Engine 切换生命周期测试 |
| `AVPlayerEngineContractTest.kt` | 新增 | iosTest 下的契约测试 |
| `AVAudioPlayerEngineContractTest.kt` | 新增 | iosTest 下的契约测试 |

**FakeEngine**：

```kotlin
class FakeEngine(
    val name: String = "FakeEngine",
    override val canHandleResult: Boolean = true,
) : PlaybackEngine {
    val events = mutableListOf<String>()  // 记录调用时序供断言
    private val _state = MutableStateFlow(PlaybackEngineState.EMPTY)
    override val state: StateFlow<PlaybackEngineState> = _state.asStateFlow()

    var loadCount = 0; var playCount = 0; var pauseCount = 0
    var releaseCount = 0
    var lastLoadedMediaData: MediaData? = null
    var lastLoadedAudio: LAudio? = null

    override fun canHandle(mediaData: MediaData, audio: LAudio): Boolean {
        events.add("canHandle(${mediaData::class.simpleName})")
        return canHandleResult
    }
    override suspend fun load(mediaData: MediaData, audio: LAudio) {
        loadCount++; lastLoadedMediaData = mediaData; lastLoadedAudio = audio
        _state.value = PlaybackEngineState(duration = 1000L, isLoading = false)
        events.add("load")
    }
    override suspend fun play() { _state.value = _state.value.copy(isPlaying = true); events.add("play") }
    override suspend fun pause() { _state.value = _state.value.copy(isPlaying = false); events.add("pause") }
    override suspend fun release() { releaseCount++; _state.value = PlaybackEngineState.EMPTY; events.add("release") }
}
```

**Router 测试示例**：

```kotlin
class PlaybackEngineRouterTest {
    @Test fun `selectEngine returns first match`() = runTest {
        val urlEngine = FakeEngine(name = "UrlEngine", canHandleResult = false)
        val bytesEngine = FakeEngine(name = "BytesEngine", canHandleResult = true)
        val router = PlaybackEngineRouter(listOf(urlEngine, bytesEngine))
        val result = router.selectEngine(MediaData.Bytes(byteArrayOf()), dummyAudio)
        assertSame(bytesEngine, result)
    }

    @Test fun `selectEngine returns null when no match`() = runTest {
        val router = PlaybackEngineRouter(listOf(
            FakeEngine(name = "NeverMatch", canHandleResult = false)
        ))
        assertNull(router.selectEngine(MediaData.Url("http://test"), dummyAudio))
    }
}
```

**Engine 切换测试示例**：

```kotlin
class AbstractPlaybackEngineTest {
    @Test fun `skipTo loads new engine and switches to it`() = runTest {
        val engine = FakeEngine(canHandleResult = true)
        val playback = createPlaybackWithEngines(listOf(engine))
        playback.queue.update { replaceAll(listOf(testAudio), 0) }
        playback.skipTo(0, start = true)
        assertEquals(1, engine.loadCount)
        assertEquals(testAudio, engine.lastLoadedAudio)
        assertEquals(1, engine.playCount)
    }

    @Test fun `skipTo releases old engine when switching`() = runTest {
        val engineA = FakeEngine(name = "A", canHandleResult = true)
        val engineB = FakeEngine(name = "B", canHandleResult = true)
        val playback = createPlaybackWithEngines(listOf(engineA, engineB))
        playback.activeEngine = engineA
        playback.skipTo(0, start = false)
        assertEquals(1, engineA.releaseCount)
        // engineB 没有被 release（它是当前活跃的）
        assertEquals(0, engineB.releaseCount)
    }
}
```

```
[ ] FakeEngine.kt — test double with call tracking
[ ] PlaybackEngineRouterTest.kt — match priority, no-match, all-match
[ ] AbstractPlaybackEngineTest.kt — load lifecycle, engine switching, completion event
[ ] AVPlayerEngineContractTest.kt — canHandle, load, release contract
[ ] AVAudioPlayerEngineContractTest.kt — canHandle, load, release contract
```

**自动化验收**：

```
☐ RouterTest: 3 个测试全部通过
☐ AbstractPlaybackTest: 5+ 个测试全部通过（skipTo 切换、Engine 复用、Engine 更换、play/pause 代理、completion 事件）
☐ iosTest: 2 个契约测试各通过
☐ ./gradlew lplayer:allTests 全部绿色
```

---

## 可测试性矩阵

| 组件 | commonTest | iosTest | 覆盖率类型 |
|------|-----------|---------|-----------|
| `PlaybackEngineRouter` | ✅ 3 个测试 | — | 逻辑覆盖 |
| `PlaybackEngineState` | ✅ data class 断言 | — | — |
| `PlaybackEngineEvent` | ✅ sealed interface 断言 | — | — |
| `AbstractPlayback.skipTo()` Engine 切换 | ✅ 5+ 个测试 | — | 逻辑覆盖 |
| `AVPlayerEngine.canHandle()` | — | ✅ 2 个测试 | 边界覆盖 |
| `AVPlayerEngine.load()` 状态变化 | — | ✅ 2 个测试 | 契约覆盖 |
| `AVPlayerEngine.load()` 实际播放 | ❌ 不可测 | ❌ 不可测 | 人工验证 |
| `AVAudioPlayerEngine.canHandle()` | — | ✅ 2 个测试 | 边界覆盖 |
| `AVAudioPlayerEngine.load()` 状态变化 | — | ✅ 2 个测试 | 契约覆盖 |
| `ActiveEngine → PlaybackState` 桥接 | ✅ 2 个测试 | — | 逻辑覆盖 |

---

## 需要注意的边界

1. **`VolumeFadeHelper`**：AVPlayerPlayback 的 `play()` 和 `pause()` 通过它淡入淡出。提取 Engine 后，VolumeFadeHelper 仍在 AVPlayerPlayback 层，不进入 Engine。这意味着 `play()` 中先调 `volumeFadeHelper.play()` 再调 `activeEngine?.play()`——音频控制始终在 playback 层。

2. **`AVPlayerItemEventObserver` / `AVPlayerPositionObserver`**：当前是全局「object」，被 AVPlayerEngine 使用。存在并发问题——如果两个 AVPlayerEngine 实例同时使用，会互相覆盖 observer。解决办法：
   - AVPlayerEngine 内部使用自己的 observer 实例，不作为全局 object 使用
   - 或将 observer 改为实例化模式

3. **AVAudioPlayer 的 delegate**：`AVAudioPlayerDidPlayToEndHelper` 是 object（单例），同样存在并发问题。AVAudioPlayerEngine 内部需要改为实例化 delegate。

4. **Playback 状态流向**：
   ```
   Engine.state (isPlaying, position, duration)
     → AbstractPlayback 收集
       → _isPlaying.value = engine.state.isPlaying
       → _currentDuration.value = engine.state.duration
   ```
   Engine 状态是**数据来源**，AbstractPlayback 的 `_isPlaying`/`_currentDuration` 成为 Engine 状态的**投影**。两者不应有分歧。

5. **MusicKitEngine 接入点**（预留）：
   - `canHandle()` 检查 `audio.mediaSourceName == "MusicKitSource"`
   - 注册顺序在 `AVAudioPlayerEngine` 之前
   - 需要扩展 `MusicKitWrapper.swift` 添加 `MusicKitPlayerController`
   - 在 MusicKitEngine 完全就绪前，MusicKit 歌曲会 fallthrough 到 AVPlayerEngine（当前行为，不改变）

---

## 进度

| 阶段 | 状态 | 开始 | 完成 | 测试数 |
|------|------|------|------|--------|
| 阶段 0：Engine 抽象定义 | ✅ | 2026-07-22 | 2026-07-22 | 0 |
| 阶段 1：AbstractPlayback 增强 | ✅ | 2026-07-22 | 2026-07-22 | 0 |
| 阶段 2：iOS Engine 提取 | ✅ | 2026-07-22 | 2026-07-22 | 0 |
| 阶段 3：单测基础设施 | ❌ | — | — | 0 |
| **总计** | **3/4** | — | — | **0** |

---

## 备注

- 本重构不修改 Android `MPlayerPlayback`（单一播放器，队列外置，不适用 Engine 架构）
- MusicKitEngine 不在本次范围，预留接口后待后续推进
- 所有测试必须能在 CI(`./gradlew lplayer:allTests`) 上稳定通过才算验收
- 阶段 1 中可以阶段性创建空壳 Engine 满足编译，阶段 2 再填入实现
