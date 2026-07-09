# 旧实体类型清理 & 完全迁移方案

> 基于 REFACTOR-LIBRARY.md 的 Phase 1-6 完成后的遗留问题
> 目标：删除 `lmedia-core/entity/` 中所有旧类型，全量切换至 `domain.model.*`

---

## 当前状态

旧 `entity/` 包（`lmedia-core/.../entity/`）16 个文件**仍然存在且被活跃使用**：

```
entity/
├── LAudio.kt          ← 含 SourceItem / Sourceable / Playable / Sortable / Linkable + buildAudio()
├── LAlbum.kt          ← 含 Sortable / Linkable
├── LArtist.kt
├── LGenre.kt          ← 含 Linkable
├── LFolder.kt         ← 含 Linkable
├── LItem.kt           ← interface LItem : Identifiable, Describable, Extensible, Linkable
├── Identifiable.kt    ← 与 domain 重复
├── Describable.kt     ← 与 domain 重复
├── TextMatchable.kt   ← 与 domain 重复
├── Available.kt       ← 与 domain 重复
├── Extensible.kt      ← 与 domain 重复
├── Linkable.kt        ← refs / ref() / flatten() — domain 无此接口
├── Metadata.kt        ← 字段与 domain.Metadata 完全相同
├── Playable.kt        ← fun sourceItem(): SourceItem — 需删除
├── Sourceable.kt      ← fun source(): String — 需删除
└── DomainConverters.kt ← toLegacyAudio() / toLegacyAlbum() 等 — 需删除
```

同时 `entity/Snapshot.kt` 已在 Phase 6 删除，但有约 8 个文件仍引用它 → **编译已损坏**。

### 引用统计

| 模块 | 引用旧实体 | 文件数 | 状态 |
|------|-----------|--------|------|
| `lplayer` 核心 | LAudio, LItem, ref, toLegacyAudio | ~8 个 | ✅ 编译通过（但有桥接） |
| `lplayer` 平台 | LAudio + SourceItem + toLegacy* | ~6 个 | 部分损坏 |
| `lhome` | LAudio, LAlbum, LArtist + ref + toLegacy* | ~8 个 | ✅ 编译通过 |
| `lalbum` | LAlbum, LAudio + toLegacy* | ~5 个 | ✅ 编译通过 |
| `lartist` | LArtist, LAudio + ref + toLegacy* | ~5 个 | ✅ 编译通过 |
| `lplaylist` | LAudio + toLegacyAudio | ~3 个 | ✅ 编译通过 |
| `lhistory` | LAudio + toLegacyAudio | ~2 个 | ✅ 编译通过 |
| `composeApp` | LAudio | 1 个 | ✅ |
| `lmedia-coil` | LAlbum, LArtist, LAudio, SourceItem, ref | ~4 个 | ✅ |
| `lmedia-ui` | **entity.Snapshot, entity.SnapshotState** | 3 个 | ❌ 已损坏 |
| `lmedia-core` Android | entity.*, Snapshot, Metadata, SourceItem | ~5 个 | ❌ 已损坏 |
| `lmedia-core` 测试 | entity.LAudio, Snapshot, Metadata | 1 个 | ❌ 已损坏 |

---

## 迁移策略总览

五阶段+附录，按依赖顺序：

```
Phase A: 修复已损坏的 Android 平台源     ← 最紧急（编译不过）
Phase B: 迁移 Playback 类型链            ← 最核心（队列类型变更）
Phase C: 迁移各平台 Playback 实现          ← 紧接 Phase B
Phase D: 迁移 Coil/UI 模块               ← 批量替换
Phase E: 删除旧 entity 包 + 清理          ← 收尾
Phase F: 修复 MediaSource DI 注册         ← 独立，随时可做
```

**关键原则：**
- 每次 Phase 提交后确保对应平台编译通过
- Priority: 修复损坏 > 迁移核心逻辑 > 批量替换 > 删除
- Sortable / SortManager 保留在 `lmedia/sortable/`，不迁移。仅取消 entity 对它的实现，ViewModels 直接从 `extra` 字段取值

---

## Phase A：修复已损坏的 Android 平台源

### 损坏原因

Phase 6 删除了 `entity/Snapshot.kt`（含 `Snapshot` data class、`SnapshotState` sealed interface、`LoadingDynamic` 变体、`buildSnapshot()` 扩展、`toComposeState()` 扩展），但以下文件未更新：

| 文件 | 损坏点 | 修复方式 |
|------|--------|---------|
| `AndroidFileSystemSource.kt` | `entity.*`、`SnapshotState.LoadingDynamic`、`songs.buildSnapshot()` | 改用 `domain.source.*` + 参数化 `buildSnapshot()` |
| `MediaStoreSource.kt` | `entity.Snapshot`、`LoadingDynamic` 判断 | 改用 domain 类型 |
| `MediaStoreScanner.kt` | `entity.*`、`buildSnapshot()` 扩展 | 改用 domain 类型 |
| `Scanner.kt` | `entity.Snapshot` import | 改用 domain 类型 |
| `AndroidFileSystemSourceContent.kt` | `entity.Snapshot`/`SnapshotState` import | 改用 domain 类型 |
| `MediaStoreSourceContent.kt` | 同上 | 改用 domain 类型 |
| `JvmFileSystemSourceContent.kt` | 同上 | 改用 domain 类型 |

### Phase A 操作清单

```
A.1  AndroidFileSystemSource.kt
  └─ 替换 import: entity.* → domain.model.* / domain.source.* + 具体 core source 类型
  └─ SourceItem.FileItem / UriItem → 将文件路径存入 extra 或从 ID 反查
  └─ LoadingDynamic → 用 domain Loading(progress, message) 替代
  └─ songs.buildSnapshot() → domain.source.buildSnapshot(songs)
  └─ stateFlow.toComposeState(scope) → 用 Compose collectAsState() 替代

A.2  MediaStoreSource.kt
  └─ 同上 import 替换
  └─ LoadingDynamic 检查 → 用 Loading 替代

A.3  MediaStoreScanner.kt
  └─ 不再使用 SourceItem / buildAudio() → 直接构造 domain.model.LAudio
  └─ buildSnapshot() → domain.source.buildSnapshot()

A.4  Scanner.kt
  └─ import 修复

A.5  AndroidFileSystemSourceContent.kt
  └─ import: entity.Snapshot → domain.source.Snapshot
  └─ import: entity.SnapshotState → domain.source.SnapshotState

A.6  MediaStoreSourceContent.kt
  └─ 同上

A.7  JvmFileSystemSourceContent.kt
  └─ 同上

A.8  SnapshotSerializationTest.kt
  └─ 改为用 domain.model.LAudio + domain.source.Snapshot
```

---

## Phase B：迁移 Playback 类型链（核心）

### 问题

`lplayer` 模块的核心类型全部绑定到 `entity.LAudio`：

```
QueueState.list: List<entity.LAudio>        ← 根类型
PlayableQueue { nextOf(), previousOf(), ... } ← 全是 entity.LAudio
QueueMutationOps { replaceAll(items: List<entity.LAudio>) }
QueueUpdateRequest { 内部操作 entity.LAudio }
QueueAction { sealed class 用 entity.LItem }
PlaybackState { Playing(val item: entity.LItem) }
Playback.updatePlaylist(playlist: List<entity.LItem>)
AbstractPlayback.resolveMedia() → List<entity.LAudio>
LItem.toPlayable() → 用 ref<LAudio>() 展开
```

### 目标

将所有上述类型从 `entity.LAudio` / `entity.LItem` 切换到 `domain.model.LAudio` / `domain.model.LItem`。

### 方案说明

#### 难点 1：`toPlayable()` 依赖 Linkable.refs

```kotlin
// 当前
internal fun LItem.toPlayable(): List<LAudio> {
    return when (this) {
        is LAudio -> listOf(this)
        else -> ref<LAudio>()  // ← 依赖 Linkable
    }
}
```

**方案**：删除 `toPlayable()`，改为：
1. `QueueMutationOps.addToStart/addToEnd/addToNext` 参数从 `LItem` 改为 `List<LAudio>`
2. 调用方在调用前自己 resolve LItem → List<LAudio>
3. 对于 screens 场景（点击专辑/歌手播放），在 ViewModel/UseCase 中先通过 repository 取得 audios

#### 难点 2：`Playback.updatePlaylist(playlist: List<LItem>)` 允许混合类型

**方案**：改为 `updatePlaylist(playlist: List<LAudio>, startIndex: Int, start: Boolean)`，仅接受 `domain.model.LAudio`
- PlayerAction.UpdateList 已经只传 List<LAudio>（通过 AudioRepository 解析）
- 屏幕调用方改为先 resolve 再传

#### 难点 3：`PlaybackState` 用 `LItem` 标记当前播放项

```kotlin
sealed class PlaybackState {
    data class Playing(val item: LItem)
    data class Paused(val item: LItem)
    data class Loading(val item: LItem)
}
```

**方案**：改为 `domain.model.LItem`
- 所有 `when(this is entity.LAudio)` → 改用 `this is domain.model.LAudio` 或改用 `when (item)` 匹配具体类型

### Phase B 操作清单

```
B.1  QueueState.kt
  └─ list: List<domain.model.LAudio>
  └─ currentItem() 返回 domain.model.LAudio?

B.2  PlayableQueue.kt
  └─ nextOf(target: domain.model.LAudio): domain.model.LAudio?
  └─ previousOf() 同理
  └─ 删除 toPlayable() 函数

B.3  QueueMutationOps.kt
  └─ addToStart(item: domain.model.LItem) → addToStart(items: List<domain.model.LAudio>)
  └─ addToEnd() / addToNext() 同理
  └─ replaceAll(items: List<domain.model.LAudio>, index: Int)
  └─ remove(item: domain.model.LAudio)
  └─ clear()

B.4  QueueUpdateRequest.kt
  └─ 跟随 QueueMutationOps 签名变更
  └─ 删除 toPlayable() 调用，改为直接操作 List<domain.model.LAudio>
  └─ pendingList: List<domain.model.LAudio>
  └─ addToStart(item) → addToStart(items: List<domain.model.LAudio>)

B.5  PlayableQueueImpl.kt
  └─ import 变更

B.6  PlaybackState.kt
  └─ item: domain.model.LItem

B.7  Playback.kt (interface)
  └─ updatePlaylist(playlist: List<domain.model.LItem>, ...)
     → updatePlaylist(playlist: List<domain.model.LAudio>, ...)
  └─ import 变更

B.8  AbstractPlayback.kt
  └─ resolveMedia() → List<domain.model.LAudio>
  └─ 删除 toLegacyAudio() 调用
  └─ updatePlaylist() 适配新签名
  └─ LItem.toPlayable() 删除 → 直接用 domain.model.LAudio

B.9  QueueAction.kt
  └─ sealed class 中的 item: domain.model.LItem

B.10 PlayerAction.kt
  └─ defaultPlayerActionHandler: UpdateList 处理中去掉 .map { it.toLegacyAudio() }
  └─ 直接传 domain.model.LAudio 给 LPlayer.instance.updatePlaylist()
```

---

## Phase C：迁移各平台 Playback 实现

紧随 Phase B，各 Playback 实现改为直接使用 `domain.model.LAudio`。

| 文件 | 当前 | 改造后 |
|------|------|--------|
| `MPlayerPlayback.kt` | 接收 entity LAudio，`toLegacyAudio()` 转换 | 直接使用 domain LAudio |
| `AVPlayerPlayback.kt` | 持有 entity LAudio，`playItem()` 内 `toDomainAudio()` | 直接使用 domain LAudio |
| `VLCPlayback.kt` | 持有 entity LAudio，用 `sourceItem` 取文件路径 | 改用 `dataSource.getMedia()` |
| `PlayerViewModel.kt` | 持有 entity LAudio，`retrieveLyric()` 内 `toDomainAudio()` | 直接使用 domain LAudio |

### VLCPlayback 特别说明

```kotlin
// 当前 — 通过 SourceItem.FileItem 获取文件路径
val path = item.sourceItem
    .let { it as? SourceItem.FileItem }
    ?.file?.absolutePath

// 改造后 — 改用 MediaDataSource
val mediaData = source.dataSource.getMedia(item)  // 返回 MediaData.Url
val path = (mediaData as? MediaData.Url)?.url     // 或转换为 VLC 可用格式
```

### Phase C 操作清单

```
C.1  MPlayerPlayback.kt
  └─ 删除 toLegacyAudio() 引入
  └─ 直接处理 domain.model.LAudio
  └─ toMediaItem() → 适配 domain.model.LAudio

C.2  AVPlayerPlayback.kt (iOS)
  └─ 删除 toDomainAudio() 函数
  └─ 队列中的 LAudio 直接是 domain 类型
  └─ playItem() 直接传 domain LAudio 给 dataSource.getMedia()

C.3  VLCPlayback.kt (JVM)
  └─ 删除 toDomainAudio() 函数
  └─ 删除 SourceItem 判断
  └─ 改为通过 dataSource.getMedia() 获取 URL

C.4  PlayerViewModel.kt
  └─ 删除 toDomainAudio() 转换
  └─ 队列中的 LAudio 直接是 domain 类型
  └─ 直接传 domain LAudio 给 getLyric()
```

---

## Phase D：迁移 Coil / UI 模块

### D.1 Coil Mapper 改造

`LAlbumMapper` 和 `LArtistMapper` 使用 `ref<LAudio>()` 来查找关联歌曲（用于封面图加载），依赖 Linkable。

**方案**：既然 domain 没有 refs，改为用 Repository 查找。但 Coil Mapper 是同步接口，不能直接调 suspend 函数。

因此：
- 将 LAlbumMapper / LArtistMapper 的 `ref<LAudio>()` 改为从 domain 对象的 `extra` 查找预埋的 ID
- 或者：从关系数据中取——但 Coil mapper 是同步的，无法查询数据库
- **推荐方案**：UI 侧传入 LAudio 而非 LAlbum/LArtist 给 Coil（调用方在获取时已经持有歌曲数据）

```
D.1  LAlbumMapper.kt
  └─ import: entity.LAlbum → domain.model.LAlbum
  └─ ref<LAudio>() 改为用 extra 存储的 audioId 构造 domain.model.LAudio
  └─ 或标记为 deprecated，由调用方直接传 LAudio

D.2  LArtistMapper.kt
  └─ 同上

D.3  MusicKitItemFetcher.kt
  └─ SourceItem.MusicKitItem → 改用 domain model 的字段
  └─ SourceItem 相关逻辑改为直接从 fetcher 参数获取

D.4  FileSourceItemFetcher.kt
  └─ SourceItem.FileItem → 改用文件路径参数
```

### D.2 UI 模块 import 替换

每个 UI 模块的核心工作是：
- `entity.LAudio` → `domain.model.LAudio`
- `entity.LAlbum` → `domain.model.LAlbum`
- `entity.LArtist` → `domain.model.LArtist`
- `entity.LItem` → `domain.model.LItem`
- `entity.Linkable` / `entity.ref` → 删除引用
- `entity.Metadata` → `domain.model.Metadata`（字段完全相同）
- `entity.toLegacyAudio()` → 删除调用
- `entity.toLegacyAlbum()` → 删除调用
- `entity.toLegacyArtist()` → 删除调用
- `entity.toLegacyLItem()` → 删除调用

```
D.5  lhome 模块 (~8 files)
  └─ HomeScreenModel: 所有实体引用 → domain
  └─ SongsVM / SongsState
  └─ SongDetailVM
  └─ SongsScreen, DailyRecommend, etc.

D.6  lalbum 模块 (~5 files)
D.7  lartist 模块 (~5 files)
D.8  lplaylist 模块 (~3 files)
D.9  lhistory 模块 (~2 files)
D.10 composeApp PlayingInfoCard
```

### D.3 ViewModel 排序适配

当前 entity 类型直接实现 `Sortable.getValueBy()`，域类型没有。排序逻辑在 `SortManager` + `doSortState` 中通过 getValueBy 调用存取字段。

**方案**：在 ViewModel 层封装一个 adapter 函数，从 domain 类型中读取所需字段：

```kotlin
// 对 domain.model.LAudio 补充排序取值
fun LAudio.getSortValue(key: String): Any? = when (key) {
    Sortable.COMPARE_KEY_ID -> id
    Sortable.COMPARE_KEY_TITLE -> metadata.title ?: title
    Sortable.COMPARE_KEY_SUB_TITLE -> metadata.artist ?: subtitle
    Sortable.COMPARE_KEY_DURATION -> metadata.duration
    // 其他字段从 metadata / extra 取
    else -> null
}
```

此函数不需要实体实现 Sortable 接口。`SortManager` 内部调用此函数而非 `getValueBy()`。

---

## Phase E：删旧清理

在 Phase A-D 全部完成后（确保无任何引用残留）：

```
E.1  删除 entity/LAudio.kt
E.2  删除 entity/LAlbum.kt
E.3  删除 entity/LArtist.kt
E.4  删除 entity/LGenre.kt
E.5  删除 entity/LFolder.kt
E.6  删除 entity/LItem.kt
E.7  删除 entity/Identifiable.kt (domain.model.Identifiable 已存在)
E.8  删除 entity/Describable.kt  (domain.model.Describable 已存在)
E.9  删除 entity/TextMatchable.kt (domain.model.TextMatchable 已存在)
E.10 删除 entity/Available.kt (domain.model.Available 已存在)
E.11 删除 entity/Extensible.kt (domain.model.Extensible 已存在)
E.12 删除 entity/Linkable.kt (domain 不保留 Linkable)
E.13 删除 entity/Metadata.kt (domain.model.Metadata 完全替代)
E.14 删除 entity/Playable.kt (接口已废弃)
E.15 删除 entity/Sourceable.kt (接口已废弃)
E.16 删除 entity/DomainConverters.kt (不再需要桥接)

E.17 更新 SnapshotSerializationTest.kt → 改用 domain 类型
E.18 全局搜索 "import com.lalilu.lmedia.entity" → 确认 0 结果
E.19 全局搜索 "toLegacyAudio\|toLegacyAlbum\|toLegacyArtist\|toLegacyLItem\|toLegacyFolder" → 0 结果
E.20 全局搜索 "entity\.LAudio\|entity\.LAlbum\|entity\.LArtist\|entity\.LItem" → 0 结果
```

---

## Phase F：MediaSource DI 注册修复

独立于上述各阶段，不影响类型编译：

```
F.1  RemoteSource.kt
  └─ @Single(createdAtStart = true) → @Single(binds = [MediaSource::class, MediaDataSource::class], createdAtStart = true)

F.2  MusicKitSource.kt
  └─ object MusicKitSource : com.lalilu.lmedia.domain.source.MediaSource
  └─ → @Single class MusicKitSource : com.lalilu.lmedia.source.MediaSource  (核心接口，含 config 默认值)
  └─ → 加 @Single(binds = [MediaSource::class])

F.3  MediaLibrarySource.kt
  └─ 同上

F.4  KoinModulesTest 更新
  └─ 验证 MediaSource 的 getAll() 能返回正确数量的源
```

---

## 影响范围总表

### 新增文件

| 文件 | 用途 |
|------|------|
| `lplayer/.../adapters/SortableAdapter.kt` | domain 类型的排序取值函数 |
| `lmedia/lmedia-ui/.../sort/SortableAdapter.kt` | 同上（UI 层可共用） |

### 修改文件分类统计

| Phase | 模块 | 文件数 | 复杂度 |
|-------|------|--------|--------|
| A | lmedia-core Android sources | 5 | ★★★ 重构 |
| A | lmedia-ui platform content | 3 | ★ 替换 import |
| A | Test | 1 | ★ 替换 import |
| B | lplayer 核心 (Queue/Playback) | 10 | ★★★★ 核心变更 |
| C | lplayer 平台实现 | 4 | ★★ 适配 |
| D | lmedia-coil | 4 | ★★ 改造 |
| D | lhome | 8 | ★ 替换 import |
| D | lalbum, lartist | 10 | ★ 替换 import |
| D | lplaylist, lhistory | 5 | ★ 替换 import |
| D | composeApp | 1 | ★ 替换 import |
| D | lmedia-ui (SortPanel) | 2 | ★★ 排序适配 |
| E | 删除旧 entity | 16 | 删除操作 |
| F | MediaSource DI | 4 | ★ 注解修改 |

### 测试验证

| Phase | 验证内容 |
|-------|---------|
| A 完成后 | 编译通过 Android 目标 |
| B 完成后 | KoinModulesTest, UseCase 测试通过 |
| C 完成后 | iOS + JVM 编译通过 |
| D 完成后 | 所有目标编译通过 |
| E 完成后 | `grep entity` 0 结果 |
| F 完成后 | 媒体数据源页面显示所有源 |

---

## 执行顺序建议

```
Phase A (修复损坏) ─── 最紧急，优先执行
    ↓
Phase F (DI修复) ───── 独立，可与 A 并行
    ↓
Phase B (Playback迁移) ─ 核心，依赖 A
    ↓
Phase C (平台Playback) ─ 依赖 B
    ↓
Phase D (Coil/UI) ──── 依赖 B（import 替换部分可提前，ref 改造需 B 完成）
    ↓
Phase E (删除旧包) ──── 依赖 A+D 全部完成
```

**建议先从 Phase A 开始，立即修复已损坏的 Android 编译，再攻 Phase B 这个硬骨头。**
