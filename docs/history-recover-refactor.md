# HistoryRecover 重构分析

## 1. 现状与问题

### 当前结构

```
HistoryRecover (object, commonMain)
  ├── recover { ids, index, position -> ... }
  │    读取 LPlayerKV → 回调传参 → 每个 Playback 自行处理
  └── startRecord(playback)
        监听 isPlaying + queue.expandedItems → 写入 LPlayerKV
```

### 使用情况

| Playback | 基类 | recover | startRecord |
|----------|------|---------|-------------|
| `MPlayerPlayback` (Android) | 直接实现 `Playback` | ✅ `run()` 中 | ✅ `run()` 中 |
| `VLCPlayback` (JVM) | `AbstractPlayback` | ❌ | ❌ |
| `AVPlayerPlayback` (iOS) | `AbstractPlayback` | ❌ | ❌ |
| `AudioPlayback` (Web) | `AbstractPlayback` | ❌ | ❌ |

### 核心痛点

1. **每个 Playback 需要手动接入** — `recover{}` + `startRecord()` 必须在初始化时显式调用
2. **`recover` 的回调含平台相关逻辑**（Media3 的 setMediaItems、seekTo），与通用逻辑混在一起
3. **`startRecord` 实际是纯通用逻辑** — 只依赖 `Playback` 接口，可以自动注入
4. **`HistoryRecover` 是 `object`** — 硬编码单例，无法插拔
5. **`MPlayerPlayback` 不继承 `AbstractPlayback`** — 逻辑不能只放在 `AbstractPlayback` 中

---

## 2. 方案设计：接口 + Impl + by 委托

### 结构

```
PlaybackHistory (interface)              ← 新增：历史记录行为定义
  ├── historyStorage: HistoryStorage
  ├── restoreFromHistory(): HistorySnapshot?
  ├── CoroutineScope.startRecording(playback: Playback)
  └── suspend fun onQueueRestored(snapshot: HistorySnapshot)

PlaybackHistoryImpl : PlaybackHistory    ← 默认实现（onQueueRestored 默认为空）

AbstractPlayback : Playback, PlaybackHistory by PlaybackHistoryImpl
MPlayerPlayback  : Playback, PlaybackHistory by PlaybackHistoryImpl
```

> **注意**：`Playback` **不**继承 `PlaybackHistory`，因为若 `Playback` 已包含 `PlaybackHistory` 接口，
> 子类中再写 `PlaybackHistory by impl` 会造成 Kotlin 冗余接口声明。
> 因此让两者作为独立接口，`AbstractPlayback` 和 `MPlayerPlayback` 各自同时实现两个接口 + `by` 委托，
> 效果等价于继承但无兼容问题。

`by` 委托让两类 Playback 共享 `PlaybackHistoryImpl` 的通用逻辑，同时可选择性 override `onQueueRestored` 做平台特定处理。

---

### 2.1 新增接口

```kotlin
// commonMain — 存储抽象，与 LPlayerKV 解耦
interface HistoryStorage {
    fun savedPlaylistIds(): List<String>
    fun savedPlayId(): String
    fun savedPosition(): Long

    fun savePlaylistIds(ids: List<String>)
    fun savePlayId(id: String)
    fun savePosition(position: Long)
}
```

```kotlin
// commonMain — 历史记录行为定义
interface PlaybackHistory {
    val historyStorage: HistoryStorage

    /** 从 KV 恢复历史快照，无历史时返回 null */
    fun restoreFromHistory(): HistorySnapshot?

    /** 开始录制播放状态到 KV */
    fun CoroutineScope.startRecording(playback: Playback)

    /**
     * 队列恢复后的生命周期回调。
     * PlaybackHistoryImpl 默认实现为空，
     * 需要做平台特定处理（如设置 Media3 position）的 Playback 可 override。
     */
    suspend fun onQueueRestored(snapshot: HistorySnapshot)

    data class HistorySnapshot(
        val ids: List<String>,
        val index: Int,
        val position: Long
    )
}
```

### 2.2 Impl 类

```kotlin
// commonMain — 桥接 LPlayerKV，不修改 LPlayerKV 本身
class HistoryStorageImpl(
    private val kv: LPlayerKV = LPlayerKV
) : HistoryStorage {
    override fun savedPlaylistIds(): List<String> = kv.historyPlaylistIds.value
    override fun savedPlayId(): String = kv.historyPlayId.value
    override fun savedPosition(): Long = kv.historyPlayPosition.value

    override fun savePlaylistIds(ids: List<String>) { kv.historyPlaylistIds.value = ids }
    override fun savePlayId(id: String) { kv.historyPlayId.value = id }
    override fun savePosition(position: Long) { kv.historyPlayPosition.value = position }
}
```

```kotlin
// commonMain — PlaybackHistory 通用实现
class PlaybackHistoryImpl(
    override val historyStorage: HistoryStorage = HistoryStorageImpl()
) : PlaybackHistory {

    override fun restoreFromHistory(): HistorySnapshot? {
        val ids = historyStorage.savedPlaylistIds()
        if (ids.isEmpty()) return null
        val id = historyStorage.savedPlayId()
        val index = ids.indexOf(id).coerceAtLeast(0)
        return HistorySnapshot(ids, index, historyStorage.savedPosition())
    }

    override suspend fun onQueueRestored(snapshot: HistorySnapshot) {
        // 默认空实现 — 平台可按需 override
    }

    override fun CoroutineScope.startRecording(playback: Playback) {
        // 监听队列变化 → 持久化 playlist 信息
        launch {
            combine(
                playback.isPlaying,
                playback.queue.expandedItems
            ) { playing, state -> Pair(playing, state) }
            .collect { (_, state) ->
                historyStorage.savePlayId(state.currentItem()?.idValue() ?: "")
                historyStorage.savePlaylistIds(state.list.map { it.idValue() })
            }
        }

        // 监听播放状态 → 持久化 position
        // 使用 transformLatest 确保前一个 position 循环在状态切换时自动取消
        playback.isPlaying
            .transformLatest { isPlaying ->
                if (isPlaying) {
                    while (isActive) {
                        historyStorage.savePosition(playback.currentPosition())
                        delay(1000.milliseconds)
                    }
                }
            }
            .launchIn(this)
    }
}
```

### 2.3 `Playback` 保持不变

`Playback` 不继承 `PlaybackHistory`，两者保持独立接口：

```kotlin
interface Playback {
    val isPlaying: StateFlow<Boolean>
    val queue: PlayableQueue
    fun currentPosition(): Long
    // ...
}
```

`PlaybackHistory` 作为独立的行为接口，由具体的 Playback 实现类通过 `by` 委托组合。

### 2.4 各 Playback 集成

**`AbstractPlayback` — 自动完成恢复 + 录制：**

```kotlin
abstract class AbstractPlayback(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob()),
    history: PlaybackHistory
) : Playback,
    CoroutineScope by coroutineScope,
    PlaybackHistory by history {

    init {
        // 自动恢复队列
        val snapshot = restoreFromHistory()
        if (snapshot != null) {
            launch {
                val items = resolveMedia(snapshot.ids)
                queue.update { replaceAll(items, snapshot.index) }
                onQueueRestored(snapshot)   // 回调来自 PlaybackHistory
            }
        }

        // 自动录制
        startRecording(this)
    }

    /** 平台必须实现：将 id 列表解析为 LAudio */
    protected abstract suspend fun resolveMedia(ids: List<String>): List<LAudio>
}
```

> `onQueueRestored` 不再属于 `AbstractPlayback`，它来自 `PlaybackHistory` 接口，
> 任何通过 `by` 委托的 Playback（包括 `MPlayerPlayback`）均可选择性 override。

**`MPlayerPlayback` — `run()` 只做恢复和录制，恢复成功后回调播放：**

```kotlin
class MPlayerPlayback(
    private val context: Context,
    private val library: Library,
    history: PlaybackHistory
) : CoroutineScope,
    Player.Listener,
    Playback,
    Runnable,
    PlaybackHistory by history {

    override val coroutineContext: CoroutineContext = Dispatchers.IO

    override fun run() {
        val browser = browserFuture.get() ?: return
        browserInstance = browser
        browser.addListener(this)

        // 只做历史恢复
        val snapshot = restoreFromHistory()
        if (snapshot != null) {
            launch {
                val items = library.mapBy<LAudio>(snapshot.ids)
                queue.update { replaceAll(items, snapshot.index) }
                onQueueRestored(snapshot)   // 进入平台回调
            }
        }

        // 录制
        startRecording(this)
        // ...
    }

    // override PlaybackHistory 的回调 — 恢复成功后设置播放器
    override suspend fun onQueueRestored(snapshot: HistorySnapshot) {
        val items = library.mapBy<LAudio>(snapshot.ids)
        val mediaIds = items.map { it.toMediaItem() }
        withContext(Dispatchers.Main) {
            browser.playWhenReady = LPlayerKV.autoPlayWhenRestart.value
            browser.setMediaItems(mediaIds, snapshot.index, snapshot.position)
            browser.prepare()
        }
    }
}
```

**`VLCPlayback` / `AVPlayerPlayback` / `AudioPlayback` — 零改动，自动继承：**

```kotlin
class VLCPlayback(library: Library) : AbstractPlayback() {
    override suspend fun resolveMedia(ids: List<String>): List<LAudio> =
        library.mapBy<LAudio>(ids)
    // onQueueRestored 使用 PlaybackHistoryImpl 的空实现
}

class AVPlayerPlayback(library: Library) : AbstractPlayback(
    coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    override suspend fun resolveMedia(ids: List<String>): List<LAudio> =
        library.mapBy<LAudio>(ids)
    // 可按需 override onQueueRestored 设置 AVPlayer position
}

class AudioPlayback(library: Library) : AbstractPlayback() {
    override suspend fun resolveMedia(ids: List<String>): List<LAudio> =
        library.mapBy<LAudio>(ids)
}
```

### 2.5 通过 DI 注入

```kotlin
// common module
single<HistoryStorage> { HistoryStorageImpl() }
single<PlaybackHistory> { PlaybackHistoryImpl(get()) }
```

---

## 3. 恢复流程对比

### 改造前（MPlayerPlayback）

```
run()
  ├── HistoryRecover.recover { ids, index, position ->    ← 读取 KV + 恢复队列 + 播放器设置全部混在一起
  │     queue.replaceAll(items, index)
  │     browser.setMediaItems(mediaIds, index, position)
  │     browser.prepare()
  │   }
  └── HistoryRecover.startRecord(this)                     ← 录制
```

### 改造后（MPlayerPlayback）

```
run()
  ├── restoreFromHistory() → snapshot                        ← 读取 KV（通用）
  │     └── launch {
  │           library.mapBy(ids) → items                    ← 平台 resolve（MPlayerPlayback 自行处理）
  │           queue.update { replaceAll(items, index) }     ← 恢复队列（通用）
  │           onQueueRestored(snapshot)                     ← 生命周期回调
  │             └── browser.setMediaItems(...)               ← 设置播放器（平台）
  │             └── browser.prepare()
  │         }
  └── startRecording(this)                                   ← 录制（通用）
```

层次清晰：`run()` 中只做恢复和录制，`onQueueRestored` 回调让平台按需处理播放器设置。

---

## 4. 影响评估

### 正面

| 维度 | 说明 |
|------|------|
| **消除重复** | `startRecord` / `recover` 不再出现在任何 Playback 中，集中在 `PlaybackHistoryImpl` |
| **`MPlayerPlayback` 也覆盖** | `by` 委托让非 `AbstractPlayback` 子类的 Playback 也能获得相同行为 |
| **平台零成本** | 新增 Playback 只需 `by PlaybackHistoryImpl()`，历史能力自动获得 |
| **可测试性** | `HistoryStorage` 可 mock，`PlaybackHistoryImpl` 可独立单元测试 |
| **LPlayerKV 零修改** | `HistoryStorageImpl` 桥接，不改变 `LPlayerKV` 的纯 KV 职责 |
| **恢复流程清晰** | `run()` / `init` 职责单一：恢复 → 回调 → 平台处理 |
| **避免并发泄漏** | `transformLatest` 替代 `onEach`，播放状态切换时自动取消前一个 position 循环 |

### 风险

| 风险 | 等级 | 说明 |
|------|------|------|
| **`resolveMedia` 需要 Library 依赖** | 中 | `AbstractPlayback` 不持有 `Library`，通过抽象方法让平台实现 |
| **协程生命周期** | 低 | 录制协程挂在 `CoroutineScope` 上，随 Playback 销毁自动结束 |
| **`MPlayerPlayback.onQueueRestored` 重复 resolve** | 低 | `library.mapBy` 执行两次（队列 + Media3），缓存命中无实质开销 |
| **`combine` 的 Experimental 注解** | 低 | 需要 `@OptIn(ExperimentalCoroutinesApi::class)` |

---

## 5. 实施步骤

1. 新建 `HistoryStorage` 接口 + `HistoryStorageImpl` 桥接
2. 新建 `PlaybackHistory` 接口（含 `onQueueRestored`）+ `PlaybackHistoryImpl`
3. 确认 `Playback` 不继承 `PlaybackHistory`，两者保持独立接口
4. `AbstractPlayback` 通过 `by` 委托 + `init` 自动恢复/录制；增加 `resolveMedia` 抽象方法；删除 `onHistoryRestored`
5. `MPlayerPlayback` 通过 `by` 委托 + 在 `run()` 中执行恢复 + override `onQueueRestored` 设置 Media3
6. `VLCPlayback` / `AVPlayerPlayback` / `AudioPlayback` 实现 `resolveMedia`
7. 删除旧的 `HistoryRecover` object

---

## 6. 结论

**可行。** `PlaybackHistory` 作为独立接口，`PlaybackHistoryImpl` 提供通用实现，`AbstractPlayback` 和 `MPlayerPlayback` 通过 `by` 委托共享。`onQueueRestored` 作为生命周期回调，在队列恢复后由平台按需处理播放器设置，录制逻辑使用 `transformLatest` 避免并发泄漏。
