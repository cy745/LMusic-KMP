# PlayableQueue 批量更新方案分析

## 1. 背景与动机

### 当前架构

`PlayableQueue` 的每个 mutation 方法（`addToStart`、`addToEnd`、`switchTo` 等）都**各自独立**地调用 `_rawQueue.update {}`，直接触发 `MutableStateFlow<QueueState>` 的一次 emit：

```kotlin
// 调用方连续执行两个操作 → 两次 emit
queue.replaceAll(items, index)
queue.switchTo(0)
```

每次 `_rawQueue.update {}` 都会：
- 使 `expandedItems` (StateFlow) 立即发射新值
- 触发所有下游 collector（UI 重组、Media3 列表同步、HistoryRecorder KV 写入）
- 产生中间态，即使调用方只关心最终结果

### 调用方现状

遍历所有调用点，常见的多步操作模式：

| 位置 | 模式 |
|------|------|
| `AbstractPlayback.setPlaybackMode()` `:146` | `queue.switchTo(...)` — 单步 |
| `AbstractPlayback.updatePlaylist()` `:113-114` | `queue.replaceAll(...)` 后跟 `skipTo(...)`，不涉及 queue |
| `AbstractPlayback.clearPlaylist()` `:123` | `queue.clear()` — 单步 |
| `MPlayerPlayback.updateItems()` `:211-217` | `queue.switchTo(...)` 或 `queue.replaceAll(...)` — 条件分支 |
| `AlbumDetailScreen` `:175` | `queue.addToNext(...)` — 单步 |
| `ArtistDetailScreen` `:175` | `queue.addToNext(...)` — 单步 |

现状是调用方基本都是单步操作，没有链式调用场景。但引入 builder 为未来复杂操作铺路的同时，也可以消除内部重复逻辑。

---

## 2. 方案设计

### 2.0 操作接口定义

将 mutation 操作提取为独立接口，`PlayableQueue` 和 `QueueUpdateRequest` 都与之关联，编译器保证二者操作集始终同步：

```kotlin
/**
 * 播放队列操作定义。
 * 只定义"做什么"（操作签名），不关心"怎么执行"（是否带 reason、是否链式）。
 * PlayableQueue 和 QueueUpdateRequest 均基于此接口，新增操作时编译器会强制两边同步。
 */
interface QueueMutationOps {
    fun addToStart(item: LItem)
    fun addToEnd(item: LItem)
    fun addToNext(item: LItem)
    fun switchTo(index: Int)
    fun replaceAll(items: List<LAudio>, index: Int)
    fun remove(item: LAudio)
    fun clear()
}
```

> 注意：`replaceAll` 在此接口中不提供 `index = -1` 默认值，避免与 `PlayableQueue` 的 `updateReason` 重载产生签名冲突。便利重载由各实现类自行提供。

### 2.1 核心类型：`QueueUpdateRequest`

实现 `QueueMutationOps`，链式返回 `this`：

```kotlin
class QueueUpdateRequest(
    private val snapshot: QueueState
) : QueueMutationOps {
    private var pendingList: List<LAudio> = snapshot.list
    private var pendingIndex: Int = snapshot.index

    override fun addToStart(item: LItem): QueueUpdateRequest {
        pendingList = item.toPlayable() + pendingList
        pendingIndex += 1
        return this
    }

    override fun addToEnd(item: LItem): QueueUpdateRequest {
        pendingList = pendingList + item.toPlayable()
        return this
    }

    override fun addToNext(item: LItem): QueueUpdateRequest {
        val targetIndex = (pendingIndex + 1).coerceIn(0, pendingList.size)
        pendingList = pendingList.toMutableList().apply {
            addAll(targetIndex, item.toPlayable())
        }
        return this
    }

    override fun switchTo(index: Int): QueueUpdateRequest {
        if (index in pendingList.indices) {
            pendingIndex = index
        }
        return this
    }

    override fun replaceAll(items: List<LAudio>, index: Int): QueueUpdateRequest {
        var targetIndex = index
        if (targetIndex == -1) {
            val currentKey = pendingList.getOrNull(pendingIndex)?.idValue()
            targetIndex = items.indexOfFirst { it.idValue() == currentKey }
        }
        pendingList = items
        pendingIndex = targetIndex.coerceAtMost(items.lastIndex)
        return this
    }

    /** 便利重载：自动计算当前播放项索引 */
    fun replaceAll(items: List<LAudio>) = replaceAll(items, -1)

    override fun remove(item: LAudio): QueueUpdateRequest {
        pendingList = pendingList.filter { it.idValue() != item.idValue() }
        return this
    }

    override fun clear(): QueueUpdateRequest {
        pendingList = emptyList()
        pendingIndex = 0
        return this
    }

    fun build(updateReason: QueueUpdateReason): QueueState = QueueState(
        list = pendingList,
        index = pendingIndex,
        updateReason = updateReason
    )
}
```

### 2.2 `PlayableQueue` 接口变更

继承 `QueueMutationOps`，增加 `updateReason` 重载，通过默认实现桥接：

```kotlin
interface PlayableQueue : QueueMutationOps {
    val expandedItems: StateFlow<QueueState>

    // ── 批量操作入口 ──
    fun batch(
        updateReason: QueueUpdateReason = QueueUpdateReason.Inner,
        block: QueueUpdateRequest.() -> Unit
    )

    // ── updateReason 重载 ──
    fun addToStart(item: LItem, updateReason: QueueUpdateReason)
    fun addToEnd(item: LItem, updateReason: QueueUpdateReason)
    fun addToNext(item: LItem, updateReason: QueueUpdateReason)
    fun switchTo(index: Int, updateReason: QueueUpdateReason)
    fun replaceAll(items: List<LAudio>, index: Int, updateReason: QueueUpdateReason)
    fun remove(item: LAudio, updateReason: QueueUpdateReason)
    fun clear(updateReason: QueueUpdateReason)

    // ── 桥接：QueueMutationOps → updateReason 重载，默认 reason = Inner ──
    override fun addToStart(item: LItem) = addToStart(item, QueueUpdateReason.Inner)
    override fun addToEnd(item: LItem) = addToEnd(item, QueueUpdateReason.Inner)
    override fun addToNext(item: LItem) = addToNext(item, QueueUpdateReason.Inner)
    override fun switchTo(index: Int) = switchTo(index, QueueUpdateReason.Inner)
    override fun replaceAll(items: List<LAudio>, index: Int) = replaceAll(items, index, QueueUpdateReason.Inner)
    override fun remove(item: LAudio) = remove(item, QueueUpdateReason.Inner)
    override fun clear() = clear(QueueUpdateReason.Inner)

    // ── 便利重载 ──
    fun replaceAll(items: List<LAudio>) = replaceAll(items, -1, QueueUpdateReason.Inner)
}
```

这样：
- 现有调用方 `queue.addToNext(album)` → 走 `QueueMutationOps` 的签名，桥接到 `addToNext(item, Inner)`
- 需要指定 reason 时 `queue.addToNext(album, Sync)` → 走 `updateReason` 重载
- 新增操作时，`QueueMutationOps` 加一个方法 → `QueueUpdateRequest` 和 `PlayableQueueImpl` 同时编译报错，不可能遗漏

### 2.3 `PlayableQueueImpl` 实现

```kotlin
class PlayableQueueImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.io + SupervisorJob())
) : PlayableQueue {
    private val _rawQueue = MutableStateFlow(QueueState())

    override val expandedItems: StateFlow<QueueState> = _rawQueue
        .stateIn(scope, SharingStarted.Lazily, QueueState())

    // ── 批量执行 ──
    override fun batch(
        updateReason: QueueUpdateReason,
        block: QueueUpdateRequest.() -> Unit
    ) {
        val request = QueueUpdateRequest(_rawQueue.value)
        request.block()
        _rawQueue.update { request.build(updateReason) }
    }

    // ── 单步方法全部委托给 batch ──
    override fun addToStart(item: LItem, updateReason: QueueUpdateReason) =
        batch(updateReason) { addToStart(item) }

    override fun addToEnd(item: LItem, updateReason: QueueUpdateReason) =
        batch(updateReason) { addToEnd(item) }

    override fun addToNext(item: LItem, updateReason: QueueUpdateReason) =
        batch(updateReason) { addToNext(item) }

    override fun switchTo(index: Int, updateReason: QueueUpdateReason) =
        batch(updateReason) { switchTo(index) }

    override fun replaceAll(items: List<LAudio>, index: Int, updateReason: QueueUpdateReason) =
        batch(updateReason) { replaceAll(items, index) }

    override fun remove(item: LAudio, updateReason: QueueUpdateReason) =
        batch(updateReason) { remove(item) }

    override fun clear(updateReason: QueueUpdateReason) =
        batch(updateReason) { clear() }
}
```

### 2.4 使用示例

```kotlin
// 批量操作
queue.batch(updateReason = QueueUpdateReason.Sync) {
    replaceAll(newItems)
    switchTo(0)
}

// 单步操作（默认 Inner）
queue.addToNext(album)
queue.replaceAll(items)

// 指定 reason
queue.replaceAll(items, updateReason = QueueUpdateReason.Sync)
```

---

## 3. 约束机制验证

新增一个操作（例如 `fun moveItem(from: Int, to: Int)`）时的编译流程：

| 步骤 | 文件 | 结果 |
|------|------|------|
| 1. 在 `QueueMutationOps` 添加方法 | `QueueMutationOps` | OK |
| 2. `QueueUpdateRequest` 未实现 | `QueueUpdateRequest` | **编译报错** |
| 3. `PlayableQueue` 未桥接 | `PlayableQueue` | **编译报错** |
| 4. `PlayableQueueImpl` 未覆盖 | `PlayableQueueImpl` | **编译报错** |

三处编译报错确保不可能遗漏同步。

---

## 4. 可行性评估

### 4.1 优势

| 维度 | 说明 |
|------|------|
| **编译期约束** | `QueueMutationOps` 确保 `PlayableQueue` 和 `QueueUpdateRequest` 操作集始终同步 |
| **原子性** | 批量内的所有操作在一次 `_rawQueue.update {}` 中完成，没有中间态 emission |
| **性能** | 减少 `StateFlow` emit 次数，下游 collector（UI、Media3 同步、HistoryRecorder）做更少无用功 |
| **一致性** | Request 在创建时固定 snapshot，链内操作不受外部并发写入干扰 |
| **渐进迁移** | 桥接方法默认 `Inner`，现有调用方 `queue.addToNext(album)` 无需修改 |
| **消除重复** | 所有 mutation 方法的内部逻辑统一委托给 `QueueUpdateRequest` |

### 4.2 风险与挑战

| 风险 | 等级 | 说明 |
|------|------|------|
| **快照竞争** | 低 | 两个并发 batch 时后 apply 的覆盖先 apply 的。与现有架构风险等同。 |
| **`replaceAll` 签名复杂度** | 低 | 接口中 `replaceAll(items, index)` 无默认值，便利重载 `replaceAll(items)` 由各实现类自行提供，不冲突。 |
| **查询方法不适用** | 低 | `previousOf`/`nextOf` 只读，不加入 `QueueMutationOps`。 |
| **Media3 同步** | 正向 | 批量 emit 将多次 diff 合并为一次，减少 Media3 操作次数。 |

---

## 5. 建议实施步骤

### Phase 1 — 基础设施 (1 commit)

1. 新建 `QueueMutationOps` 接口
2. 新建 `QueueUpdateRequest` 类，实现 `QueueMutationOps`
3. 在 `PlayableQueue` 中继承 `QueueMutationOps`，增加 `updateReason` 重载和桥接默认实现

### Phase 2 — 内部重构 (1 commit)

4. `PlayableQueueImpl` 全部委托给 `batch {}`
5. 验证编译通过、功能正常

### Phase 3 — 调用方优化（可选）

6. 识别真实批量场景，改为 `batch {}` 调用

---

## 6. 结论

**方案可行，且编译期约束解决了「新增方法遗漏同步」的问题。**

核心改动：
- 新增 1 个接口（`QueueMutationOps`，~10 行）
- 新增 1 个类（`QueueUpdateRequest`，~75 行）
- `PlayableQueue` 继承 `QueueMutationOps` + 7 个桥接默认实现
- `PlayableQueueImpl` 全部委托给 `batch {}`

`QueueUpdateReason` 保持 `Unknown | Inner | Sync` 不变。现有调用方零改动。
