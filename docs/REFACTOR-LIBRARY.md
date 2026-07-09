# Library Clean Architecture 重构任务文档

> 确认版 v2.0 — 所有决策已与用户确认  
> 状态：**全部完成**（151 文件，+4361/-2265 行，21 次提交）  
> 参考：https://juejin.cn/post/7623242804392247305

---

## 执行摘要

```
──────────────────────────────────────────────────────
  阶段                            状态  提交    文件
──────────────────────────────────────────────────────
  Phase 1  lmedia-domain + Entity + 接口    ✅    b7eb799  37
  Phase 2  Room Entity + Mapper + DAO + 测试  ✅    (3 次)  32
  Phase 3  UseCase + 测试                  ✅    59637af  13
  Phase 4  消费者改造 (VM/Playback/PlayerAction) ✅    074a8ac  17
  Phase 5  清理删除 Library/LMedia          ✅    d1b742e  5
  Phase 6  MediaSource → 领域接口迁移        ✅    (8 次)  33
──────────────────────────────────────────────────────
  测试覆盖          
  ├── Mapper + Repository 测试               ✅    55 用例
  ├── UseCase 测试                           ✅    17 场景
  ├── DAO 测试                               ✅    31 用例
  ├── Fake 测试                              ✅    4 用例
  └── Koin DI 验证 (jvmTest)                 ✅    5 用例
──────────────────────────────────────────────────────
```

### 关键数据

| 指标 | 值 |
|------|-----|
| 新增文件 | ~55 个 |
| 修改文件 | ~96 个 |
| 删除文件 | ~12 个 |
| 总代码变动 | +4361 / -2265 |
| 构建通过 | JVM / Android / iOS |
| 测试通过 | 44 用例，全部 ✅ |

### 遗留问题（非阻塞）

1. **`MusicKitSource` / `MediaLibrarySource`** — 没有 `@Single(binds = [MediaSource::class])`，需要补上才能被 `getAll<MediaSource>()` 发现
2. **`RemoteSource`** — `@Single(createdAtStart = true)` 缺少 `binds = [...MediaSource::class]`
3. **`SubsonicSource`** — 需确认 `@Single(binds = [...])` 绑定正确

---

## 一、现状分析

### 1.1 当前架构

```
Consumer (ViewModel / PlayerAction / Playback)
    ↓ 直接调用 LMedia.instance.flow<>() / mapBy<>() / get<>()
LMedia (extends Library)  ← 单例，全局静态访问
    ↓ 通过 KClass when 分支匹配到对应 DAO
Database DAOs (LAudioDao / LAlbumDao / LArtistDao / ...)
    ↑ 数据写入
MediaSource.source() → Snapshot → database.mediaDao().insert()
```

### 1.2 核心问题

| # | 问题 | 严重程度 |
|---|------|----------|
| 1 | **LMedia.instance 全局静态访问** — 各处硬编码 `LMedia.instance.flow<>()`，无法测试、无法替换 | 🔴 |
| 2 | **Library 是上帝类** — 通过 `getSourcesFlowByClass<T>()` + `when(KClass)` 分发到不同 DAO，新增实体必须改此类 | 🔴 |
| 3 | **ViewModel 承载业务逻辑** — `HomeScreenModel.requireUpdateDailyRecommends()`、`ArtistDetailVM.loadRelatedArtists()` 都是业务规则，不应放在 VM | 🟡 |
| 4 | **Entity 不纯** — `LAudio` 身兼三职：Room 实体、领域模型、数据源传递对象，含 `@Entity`/`SourceItem`/`Linkable.refs` 等，两边都动弹不得 | 🔴 |
| 5 | **Playback 层耦合** — `MPlayerPlayback` 构造注入 `Library`，`AVPlayerPlayback` 用 `LMedia.instance`，各平台不统一 | 🟡 |
| 6 | **重复的过滤逻辑** — 多个 State 类中有一模一样的关键词筛选代码 | 🟡 |

### 1.3 已存在的 Repository 模式参照

`lplaylist` 模块已经实现了标准 Clean Architecture Repository 模式：

```
PlaylistRepository (接口)      ← Domain
PlaylistRepositoryImpl (@Single) ← Data
PlaylistDetailVM → 注入 PlaylistRepository
```

---

## 二、目标架构【已确认】

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Compose / ViewModel)                      │
│  lhome, lalbum, lartist, lplayer, lhistory, ...      │
│  ViewModel 只做四件事：                                │
│    ① 持有 UiState                                     │
│    ② 响应 UI 事件                                     │
│    ③ 调用 UseCase                                     │
│    ④ 结果映射到 UiState                               │
├─────────────────────────────────────────────────────┤
│  Domain Layer (纯 Kotlin, 无平台依赖)                  │
│  lmedia-domain 模块                                   │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Entity (纯数据类，无 Room/Compose 依赖)：         │ │
│  │  - LAudio, LAlbum, LArtist, LGenre, LFolder      │ │
│  │  - LItem, Identifiable, Describable              │ │
│  │  - Extensible (不含 Linkable.refs)               │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  MediaSource 接口：                                │ │
│  │  - MediaSource (source(): Flow<Snapshot>)         │ │
│  │  - MediaData (Url/Bytes)                         │ │
│  │  - MediaDataSource (getLyric/getPicture/getMedia) │ │
│  │  - PlatformMediaSource 接口                       │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  Snapshot (引用 Domain Entity)                    │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  Repository 接口：                                │ │
│  │  - AudioRepository                               │ │
│  │  - AlbumRepository                               │ │
│  │  - ArtistRepository                              │ │
│  │  - GenreRepository                               │ │
│  │  - FolderRepository                              │ │
│  │  - MediaSourceBindingRepository (管理 Source 绑定)│ │
│  ├─────────────────────────────────────────────────┤ │
│  │  UseCase：                                       │ │
│  │  - SearchAudiosUseCase                           │ │
│  │  - GetDailyRecommendsUseCase                     │ │
│  │  - GetRelatedArtistsUseCase                      │ │
│  └─────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────┤
│  Data Layer (Room + Platform)                        │
│  lmedia-data, lmedia-core                            │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Room Entity（数据库专用，含 @Entity/@PrimaryKey）： │ │
│  │  - LAudioEntity, LAlbumEntity, ...               │ │
│  │  - Mapper: LAudioEntity ↔ LAudio                 │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  Repository 实现：                                │ │
│  │  - AudioRepositoryImpl                           │ │
│  │  - AlbumRepositoryImpl                           │ │
│  │  - ArtistRepositoryImpl                          │ │
│  │  - GenreRepositoryImpl                           │ │
│  │  - FolderRepositoryImpl                          │ │
│  │  - MediaSourceBindingRepositoryImpl              │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  Database (Room DAOs) — 保留不变                  │ │
│  ├─────────────────────────────────────────────────┤ │
│  │  Platform MediaSource 实现（各平台）               │ │
│  └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.1 模块依赖关系

```
lmedia-domain (纯 Kotlin, 最底层)
    ↑ 依赖
lmedia-core (平台实现: PlatformMediaSource, Taglib 等)
lmedia-data (Room, RepositoryImpl → 依赖 lmedia-domain)
    ↑ 各 feature 模块依赖
lhome, lalbum, lartist, lplayer, lhistory, lplaylist, composeApp
```

### 2.2 模块迁移内容

| 类/接口 | 当前模块 | 目标模块 | 说明 |
|---------|---------|---------|------|
| `LAudio` (纯数据) | lmedia-core | **lmedia-domain** | 移除 `@Entity`/`sourceItem`/`refs`/`Linkable`/`Sortable`/`Sourceable`/`Playable` |
| `LAlbum` (纯数据) | lmedia-core | **lmedia-domain** | 同上 |
| `LArtist` (纯数据) | lmedia-core | **lmedia-domain** | 同上 |
| `LGenre` (纯数据) | lmedia-core | **lmedia-domain** | 同上 |
| `LFolder` (纯数据) | lmedia-core | **lmedia-domain** | 同上 |
| `LItem` 接口 | lmedia-core | **lmedia-domain** | |
| `Identifiable`, `Describable`, `TextMatchable`, `Available` | lmedia-core | **lmedia-domain** | 纯接口 |
| `Extensible` + `extensibleImpl` | lmedia-core | **lmedia-domain** | |
| `Snapshot` | lmedia-core | **lmedia-domain** | 引用 Domain Entity |
| `MediaSource` 接口 | lmedia-core | **lmedia-domain** | |
| `MediaData` (Url/Bytes) | lmedia-core | **lmedia-domain** | |
| `MediaDataSource` | lmedia-core | **lmedia-domain** | |
| `PlatformMediaSource` | lmedia-core | **lmedia-domain** | 接口声明移到 domain |
| `buildSnapshot`/`buildRelations` | lmedia-core | **lmedia-domain** | |
| `Metadata` | lmedia-core | **lmedia-domain** | 纯数据类 |
| `BuildAudioScope`/`buildAudio` | lmedia-core | **lmedia-domain** | 纯辅助函数 |
| `SourceItemDefaults` | lmedia-core | **lmedia-domain** | `Empty`/`RequestUrl` 作为纯标记 |
| `LAudioEntity` | — | **lmedia-data** | 新增 Room Entity |
| `LAlbumEntity` | — | **lmedia-data** | 新增 Room Entity |
| 各 DAO | lmedia-data | lmedia-data | **保留不动，仅泛型引用调整** |
| `Linkable` | lmedia-core | lmedia-core | 保留，只在 data 层组装关系时使用 |
| `Sortable` | lmedia-core | lmedia-core | 保留，排序在 ViewModel 层 |
| `SourceItem` (expect/actual) | lmedia-core | — | **删除**，各平台 MediaSource 自主管理 |
| `PlatformMediaSource` 平台实现 | lmedia-core | lmedia-core | 保留不动 |
| `Taglib` 相关 | lmedia-core | lmedia-core | 保留不动 |
| `MediaSource` 平台实现 | lmedia-core | lmedia-core | 保留不动 |

---

## 三、关键设计决策【已确认】

| # | 决策项 | 结论 |
|---|--------|------|
| 1 | 模块结构 | **方案A**：在 `lmedia/` 内新增 `lmedia-domain` 子模块 |
| 2 | Entity 分离 | **三道分离**：Domain `LAudio` ↔ Room `LAudioEntity`，中间加 Mapper |
| 3 | SourceItem | **从 LAudio 移除**，由各平台 MediaSource 自主管理 ID→SourceItem 映射 |
| 4 | Linkable.refs | **剥离到 Data 层**，Domain Model 不携带 refs。关联查询用 UseCase 封装 |
| 5 | Repository 粒度 | **5 个独立**：Audio / Album / Artist / Genre / Folder |
| 6 | UseCase 范围 | **先 3 个**：SearchAudiosUseCase / GetDailyRecommendsUseCase / GetRelatedArtistsUseCase |
| 7 | 实体命名 | Domain = `LAudio`, Room = `LAudioEntity` |
| 8 | 模块依赖 | Domain **作为最底层**，lmedia-core 和 lmedia-data 都依赖它 |
| 9 | 排序逻辑 | **保留在 ViewModel 层**，不提取 UseCase |
| 10 | PlayerAction | Koin 内联获取 Repository（架构上应重构为独立 Service + ViewModel，本次不做） |
| 11 | Library/LMedia | **最终删除/降级**，LMedia → MediaSourceBindingRepositoryImpl |

---

## 四、详细任务拆解

### 第一阶段：创建 lmedia-domain 模块 + 迁移 Entity/核心接口

#### 任务 1.1：创建 lmedia-domain 模块（工程搭建）

- [ ] 新建 `lmedia/lmedia-domain/build.gradle.kts`
  - 纯 Kotlin 模块（kotlinMultiplatform）
  - 不引入 Room3 / Compose / Ktor
  - 依赖：`kotlinx-serialization`、`kotlinx-coroutines-core`、`kotlinx-io`
- [ ] 项目根 `settings.gradle.kts` 引入 `:lmedia:lmedia-domain`
- [ ] 创建包结构：
  - `com.lalilu.lmedia.domain.model`
  - `com.lalilu.lmedia.domain.repository`
  - `com.lalilu.lmedia.domain.usecase`
  - `com.lalilu.lmedia.domain.source`
- [ ] 创建 `LMediaDomainModule.kt`（Koin `@Module` + `@ComponentScan`）

#### 任务 1.2：迁移 Domain Entity（从 lmedia-core）

- [ ] 将以下类从 lmedia-core **复制**到 lmedia-domain，**清理后**作为 Domain 纯数据类：
  - `LAudio` → 移除：`@Entity`/`@PrimaryKey`/`@ColumnInfo`/`@Ignore`/`@Transient`、`sourceItem`、`refs`/`Linkable`、`Sortable`、`Sourceable`、`Playable` 接口
  - `LAlbum` → 同上，移除 `Sortable`、`refs`/`Linkable`
  - `LArtist` → 同上
  - `LGenre` → 移除 `refs`/`Linkable`
  - `LFolder` → 移除 `refs`/`Linkable`
- [ ] 迁移纯接口：`LItem`、`Identifiable`、`Describable`、`TextMatchable`、`Available`、`Extensible`
- [ ] 迁移纯数据类：`Metadata`、`SourceItemDefaults`（仅保留 `Empty`/`RequestUrl` 纯标记）
- [ ] 迁移辅助函数：`extensibleImpl`、`BuildAudioScope`、`buildAudio`（移除 `context(source: MediaSource)` 约束）

#### 任务 1.3：迁移 Snapshot 和 MediaSource 接口定义

- [ ] `Snapshot` → 引用 `lmedia-domain` 中的 LAudio 等
- [ ] `buildSnapshot()`、`buildRelations()`、`SnapshotState`
- [ ] `MediaSource` 接口
- [ ] `MediaData` (Url/Bytes)
- [ ] `MediaDataSource`
- [ ] `PlatformMediaSource` 接口定义（expect 声明）

#### 任务 1.4：调整 lmedia-core

- [ ] `lmedia-core/build.gradle.kts` → 新增依赖 `:lmedia:lmedia-domain`
- [ ] 删除已迁移到 domain 的源文件（见 2.2 表）
- [ ] 保留：`PlatformMediaSource` 的平台 actual 实现、`SourceItem` 相关 platform 代码清理、`Taglib` 相关、`MediaSource` 平台实现、`Linkable`、`Sortable`
- [ ] `Snapshot.toComposeState()` → 移到 domain 或改为扩展函数在 UI 层

#### 任务 1.5：调整 lmedia-data

- [ ] `lmedia-data/build.gradle.kts` → 新增依赖 `:lmedia:lmedia-domain`

#### 任务 1.6：处理 Linkable 在 Core 中的遗留引用

- Linkable 保留在 lmedia-core，Data 层的 DAO 仍使用它来组装关系
- lmedia-core 的 Linkable 引用 `lmedia-domain` 中的 Entity 来定义 `refs` 的类型

#### 任务 1.7：创建测试基础设施

- [ ] 在 lmedia-domain 的 `commonTest` 中创建 `fake/` 目录
- [ ] 实现 `FakeAudioRepository` — 基于 `MutableStateFlow` 的完整 Fake
- [ ] 实现 `FakeAlbumRepository`
- [ ] 实现 `FakeArtistRepository`
- [ ] 实现 `FakeGenreRepository`
- [ ] 实现 `FakeFolderRepository`
- [ ] 实现 `FakeMediaSourceBindingRepository`
- [ ] 创建 `FakeEntityFactory.kt` — 快速构造测试实体的工厂函数
- [ ] 验证：`lmedia-domain/build.gradle.kts` 的 `commonTest` 依赖配置正确（`kotlin-test` + `kotlinx-coroutines-test`）

### 第二阶段：Room Entity + Mapper + Repository 实现

#### 任务 2.1：创建 Room Entity（在 lmedia-data）

- [ ] `LAudioEntity` — 基于当前 LAudio，包含 `@Entity`/`@PrimaryKey`/`@ColumnInfo`，移除 `sourceItem`、`sourceItem` 相关
  - 持有关联关系 ID（albumId、artistId 等）而非 Linkable refs
- [ ] `LAlbumEntity` — 类似
- [ ] `LArtistEntity` — 类似
- [ ] `LGenreEntity` — 类似
- [ ] `LFolderEntity` — 类似
- [ ] 调整现有 DAO 中的 SQL 表名列名指向 Entity 类

#### 任务 2.2：创建 Mapper（在 lmedia-data）

- [ ] `AudioMapper` — `LAudioEntity ↔ LAudio` 双向映射
- [ ] `AlbumMapper` — `LAlbumEntity ↔ LAlbum`
- [ ] `ArtistMapper` — `LArtistEntity ↔ LArtist`
- [ ] `GenreMapper` — `LGenreEntity ↔ LGenre`
- [ ] `FolderMapper` — `LFolderEntity ↔ LFolder`

#### 任务 2.3：调整 DAO

- [ ] `LAudioDao` → 操作 `LAudioEntity`，公开方法返回值经 mapper 转为 Domain `LAudio`
  - `getAllAudioWithRelations()` → `getAllAudioEntities()` + mapper
  - `getAudios(ids)` → 同理
- [ ] 其他 DAO 同理
- [ ] `LMediaDao.insert(snapshot)` → 接收 Domain Snapshot，内部 mapper 转为 Entity 再插入

#### 任务 2.4：实现 Repository

- [ ] `AudioRepositoryImpl` — 注入 `LAudioDao`，内部 mapper
- [ ] `AlbumRepositoryImpl` — 注入 `LAlbumDao`
- [ ] `ArtistRepositoryImpl` — 注入 `LArtistDao`
- [ ] `GenreRepositoryImpl` — 注入 `LGenreDao`
- [ ] `FolderRepositoryImpl` — 注入 `LFolderDao`
- [ ] `MediaSourceBindingRepositoryImpl` — 从 `LMedia.startSourceBinding()` 提取
  - 注入 `PlatformMediaSource` + `ILMediaDatabase`
  - 注入的 database 操作 `LAudioEntity` 等

#### 任务 2.5：Koin 注册

- [ ] `LMediaDataModule` → `@ComponentScan("com.lalilu.lmedia.data")` 覆盖新增 RepositoryImpl
- [ ] `@Single(binds = [AudioRepository::class])` 等绑定

#### 任务 2.6：Mapper + Repository 单元测试

- [ ] `AudioMapperTest` — 正向/逆向映射 + 边界值
- [ ] `AlbumMapperTest`
- [ ] `ArtistMapperTest`
- [ ] `GenreMapperTest`
- [ ] `FolderMapperTest`
- [ ] 配置 lmedia-data 的 `commonTest` Room in-memory 数据库支持（`Room.inMemoryDatabaseBuilder`）
- [ ] `AudioRepositoryImplTest` — CRUD + 空数据 + ID 列表
- [ ] `AlbumRepositoryImplTest`
- [ ] `ArtistRepositoryImplTest`
- [ ] `GenreRepositoryImplTest`
- [ ] `FolderRepositoryImplTest`

### 第三阶段：UseCase 实现

#### 任务 3.1：SearchAudiosUseCase

统一关键词筛选，替代以下重复代码：

| 文件 | 行数 | 替换 |
|------|------|------|
| `SongsState.getSongsFlow()` | ~15 行 | → 用 SearchAudiosUseCase |
| `AlbumDetailState.getSongsFlow()` | ~15 行 | → 用 SearchAudiosUseCase |
| `ArtistDetailState.getSongsFlow()` | ~15 行 | → 用 SearchAudiosUseCase |
| `PlaylistDetailState.getSongsFlow()` | ~15 行 | → 用 SearchAudiosUseCase |

#### 任务 3.2：GetDailyRecommendsUseCase

从 `HomeScreenModel` 提取每日推荐逻辑：
- `needsRefresh(): Flow<Boolean>`
- `get(): List<LItem>`
- `refresh()` — 随机选择 10 首歌曲 + 2 专辑 + 2 歌手

#### 任务 3.3：GetRelatedArtistsUseCase

从 `ArtistDetailVM.loadRelatedArtists()` 提取：
- 获取某个歌手的关联歌手（通过共同歌曲）

#### 任务 3.4：UseCase 单元测试

- [ ] `SearchAudiosUseCaseTest` — 空关键词/单关键词/多关键词/大小写/IDS 筛选/空数据等 6 个场景
- [ ] `GetDailyRecommendsUseCaseTest` — 空数据/需要刷新/不需要刷新/生成推荐列表 4 个场景
- [ ] `GetRelatedArtistsUseCaseTest` — 有共同歌曲/无共同歌曲/排除自身 3 个场景

### 第四阶段：消费者改造

#### 任务 4.1：ViewModel 改造总表

| ViewModel | 当前访问方式 | 改造后 |
|-----------|-------------|--------|
| `HomeScreenModel` | `library.flow<>()`/`get<>()`/`mapByByPrefix()`/`LMedia.instance.flow<>()` | 注入 `AudioRepo` + `AlbumRepo` + `ArtistRepo` + `GetDailyRecommendsUseCase` |
| `SongsVM` + `SongsState` | `LMedia.instance.flow<LAudio>()` / `.mapByFlow()` | 注入 `AudioRepo` 或 `SearchAudiosUseCase` |
| `SongDetailVM` | `LMedia.instance.flow<LAudio>(id)` / `.mapByByPrefix()` | 注入 `AudioRepo` + `AlbumRepo` + `ArtistRepo` |
| `AlbumsVM` + `AlbumsState` | `LMedia.instance.flow<LAlbum>()` | 注入 `AlbumRepo` |
| `AlbumDetailVM` + `AlbumDetailState` | `LMedia.instance.flow<LAlbum>(id)` | 注入 `AlbumRepo` + `AudioRepo` + `SearchAudiosUseCase` |
| `ArtistsVM` + `ArtistsState` | `LMedia.instance.flow<LArtist>()` | 注入 `ArtistRepo` |
| `ArtistDetailVM` + `ArtistDetailState` | `LMedia.instance.flow<LArtist>()` / `.get<>()` / `.mapBy<>()` | 注入 `ArtistRepo` + `AudioRepo` + `GetRelatedArtistsUseCase` |
| `HistoryVM` | `LMedia.instance.mapByFlow<LAudio>(ids)` | 注入 `AudioRepo` |
| `PlaylistDetailVM` + `PlaylistDetailState` | `LMedia.instance.mapByFlow<LAudio>(ids)` | 注入 `AudioRepo` 或 `SearchAudiosUseCase` |

#### 任务 4.2：Playback 层改造

| 实现 | 当前 | 改造后 |
|------|------|--------|
| `MPlayerPlayback` (Android) | `library.mapBy<LAudio>(ids)` — 构造注入 `Library` | `AudioRepository` |
| `AVPlayerPlayback` (iOS) | `LMedia.instance.mapBy<LAudio>(ids)` — 静态访问 | `AudioRepository`（Koin 构造注入，已验证无跨线程问题） |
| `VLCPlayback` (JVM) | 待确认 | `AudioRepository` |
| `AbstractPlayback` | `abstract suspend fun resolveMedia()` | 签名或实现方式调整 |

#### 任务 4.3：PlayerAction 改造

- `PlayerAction.UpdateList` 中的 `LMedia.instance.mapBy<LAudio>(action.ids)` → 改为 `koinInject<AudioRepository>().getAudios(action.ids)`
- 架构上 PlayerAction 应重构为独立 Service + ViewModel 模式，此为过渡方案（见决策 10）

#### 任务 4.4：Screen 层调整

- 检查各个 Screen 中有无直接引用 `LMedia.instance` 或 `Library`，如有改为 Repository 注入

### 第五阶段：收尾清理

#### 任务 5.1：删除 Library 抽象类

- [ ] `lmedia-data/.../data/Library.kt` → 删除
- [ ] 检查无残留引用

#### 任务 5.2：LMedia 降级

- [ ] 删除 `LMedia` 中所有公开数据查询方法
- [ ] 删除 `companion object { lateinit var instance }`
- [ ] `LMedia` 改名/降级为 `MediaSourceBindingRepositoryImpl`（如果不需要保留）
- [ ] 确认 SourceBinding 逻辑已迁移到 `MediaSourceBindingRepositoryImpl`

#### 任务 5.3：全局清理

- [ ] 全局搜索 `LMedia.instance` → 0 结果
- [ ] 全局搜索 `import com.lalilu.lmedia.data.Library` → 0 结果
- [ ] 全局搜索 `import com.lalilu.lmedia.data.LMedia` → 仅在 Koin 注册处可接受
- [ ] 全局搜索 `import com.lalilu.lmedia.entity` → 全部改为 `lmedia.domain.model`（`Linkable`/`Sortable` 除外）
- [ ] 确认所有 `build.gradle.kts` 中的依赖配置正确

---

## 五、数据流变化对比

### 当前流

```
ViewModel
    ↓ LMedia.instance.flow<LAudio>()
LMedia (单例, 跨线程)
    ↓ getSourcesFlowByClass(LAudio::class)
database.audioDao().getAllAudio()
    ↓ with relations mapping (link refs)
Flow<List<LAudio>> (含 refs/sourceItem)
```

### 重构后流

```
ViewModel
    ↓ (构造注入)
AudioRepository (接口在 lmedia-domain)
    ↓
AudioRepositoryImpl (在 lmedia-data)
    ↓ audioDao.getAllAudioEntities()
    ↓ LAudioEntity → Mapper → LAudio (Domain, 无 refs/sourceItem)
Flow<List<LAudio>> (纯 Domain Model)
```

### 业务逻辑流

```
ViewModel (onSearchClick)
    ↓ 调用
SearchAudiosUseCase(keywords = ["周杰伦"])
    ↓ 内部
AudioRepository.getAudios()
    ↓ mapLatest filter
Flow<List<LAudio>> (已筛选)
```

---

## 六、改动清单（全部文件）

### 新增文件（约 45 个）

```
lmedia/lmedia-domain/
├── build.gradle.kts
├── src/commonMain/kotlin/com/lalilu/lmedia/domain/
│   ├── LMediaDomainModule.kt
│   ├── model/
│   │   ├── LAudio.kt              ← 纯数据类，无 Room/refs/SourceItem
│   │   ├── LAlbum.kt
│   │   ├── LArtist.kt
│   │   ├── LGenre.kt
│   │   ├── LFolder.kt
│   │   ├── LItem.kt
│   │   ├── Identifiable.kt
│   │   ├── Describable.kt
│   │   ├── TextMatchable.kt
│   │   ├── Available.kt
│   │   ├── Extensible.kt
│   │   └── Metadata.kt
│   ├── source/
│   │   ├── MediaSource.kt
│   │   ├── MediaData.kt
│   │   ├── MediaDataSource.kt
│   │   ├── PlatformMediaSource.kt
│   │   ├── Snapshot.kt
│   │   └── SourceItemDefaults.kt
│   ├── repository/
│   │   ├── AudioRepository.kt
│   │   ├── AlbumRepository.kt
│   │   ├── ArtistRepository.kt
│   │   ├── GenreRepository.kt
│   │   ├── FolderRepository.kt
│   │   └── MediaSourceBindingRepository.kt
│   └── usecase/
│       ├── SearchAudiosUseCase.kt
│       ├── GetDailyRecommendsUseCase.kt
│       └── GetRelatedArtistsUseCase.kt

lmedia/lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/
├── entity/
│   ├── LAudioEntity.kt
│   ├── LAlbumEntity.kt
│   ├── LArtistEntity.kt
│   ├── LGenreEntity.kt
│   └── LFolderEntity.kt
├── mapper/
│   ├── AudioMapper.kt
│   ├── AlbumMapper.kt
│   ├── ArtistMapper.kt
│   ├── GenreMapper.kt
│   └── FolderMapper.kt
└── repository/
    ├── AudioRepositoryImpl.kt
    ├── AlbumRepositoryImpl.kt
    ├── ArtistRepositoryImpl.kt
    ├── GenreRepositoryImpl.kt
    ├── FolderRepositoryImpl.kt
    └── MediaSourceBindingRepositoryImpl.kt
```

### 修改文件（约 25 个）

```
settings.gradle.kts                                  ← 引入 :lmedia:lmedia-domain

lmedia/lmedia-core/build.gradle.kts                   ← 依赖 :lmedia:lmedia-domain，删除已迁移文件
lmedia/lmedia-data/build.gradle.kts                   ← 依赖 :lmedia:lmedia-domain
lmedia/lmedia-data/.../LMediaDataModule.kt            ← 扩展 ComponentScan
lmedia/lmedia-data/.../database/LAudioDao.kt          ← 操作 LAudioEntity + mapper
lmedia/lmedia-data/.../database/LAlbumDao.kt           ← 同理
lmedia/lmedia-data/.../database/LArtistDao.kt          ← 同理
lmedia/lmedia-data/.../database/LGenreDao.kt           ← 同理
lmedia/lmedia-data/.../database/LFolderDao.kt          ← 同理
lmedia/lmedia-data/.../database/LMediaDao.kt           ← insert(snapshot) 调整
lmedia/lmedia-data/.../relation/CombinedQueryRelations.kt ← 引用 Entity 类
lmedia/lmedia-data/.../relation/CrossRef*.kt            ← 引用 Entity 类

lhome/.../viewmodel/HomeScreenModel.kt                ← 注入 Repository + UseCase
lhome/.../viewmodel/SongsVM.kt                        ← 注入 AudioRepository
lhome/.../viewmodel/SongDetailVM.kt                   ← 注入 Repository 替代 mapByByPrefix
lhome/.../screen/songs/SongsScreen.kt                 ← 引用调整（如需要）

lalbum/.../viewmodel/AlbumsVM.kt                      ← 注入 AlbumRepository
lalbum/.../viewmodel/AlbumDetailVM.kt                 ← 注入 Repository + UseCase

lartist/.../viewmodel/ArtistsVM.kt                    ← 注入 ArtistRepository
lartist/.../viewmodel/ArtistDetailVM.kt               ← 注入 Repository + UseCase

lhistory/.../viewmodel/HistoryVM.kt                   ← 注入 AudioRepository

lplaylist/.../viewmodel/PlaylistDetailVM.kt           ← 注入 AudioRepository

lplayer/.../action/PlayerAction.kt                    ← Koin 内联获取 AudioRepository
lplayer/.../playback/AbstractPlayback.kt              ← resolveMedia 调整
lplayer/src/androidMain/.../MPlayerPlayback.kt        ← 注入 AudioRepository
lplayer/src/iosMain/.../AVPlayerPlayback.kt           ← 构造注入 AudioRepository
lplayer/src/jvmMain/.../VLCPlayback.kt                ← 构造注入 AudioRepository
```

### 删除文件（约 12 个，Phase 1 和 Phase 5 各一批）

```
Phase 1 中从 lmedia-core 删除（已迁移到 domain）:
  entity/LAudio.kt, LAlbum.kt, LArtist.kt, LGenre.kt, LFolder.kt
  entity/LItem.kt, Snapshot.kt
  source/MediaSource.kt, PlatformMediaSource.kt

Phase 5 中删除:
  data/Library.kt, data/LMedia.kt → 降级
```

---

## 七、边界情况与注意事项

### 7.1 iOS Koin 注入 vs `LMedia.instance`

当前 `AVPlayerPlayback` 用 `LMedia.instance` 是因为 Koin 注入在 Kotlin/Native 上有跨线程问题（SIGSEGV vtable dispatch）。Repository 模式中：

- `AudioRepository` 的注入发生在**构造函数时**（Playback 类构造时由 Koin 注入）
- DAO 访问只在 Repository 方法的 `suspend` 函数内部进行，不涉及跨协程传递对象引用
- Room 的 Flow 返回是冷流，collect 时在当前协程执行
- `AudioRepositoryImpl` 是 `@Single`，Koin 容器初始化时创建一次，之后所有协程共用

**结论：不会有跨线程问题。`AVPlayerPlayback` 可以安全地通过构造函数注入 `AudioRepository`。**

### 7.2 `mapByByPrefix()` 替代

`Library.getByPrefix()` / `mapByByPrefix()` 是跨实体类型的混合查询，在 `HomeScreenModel` 和 `SongDetailVM` 中被使用。

替代策略：
- `HomeScreenModel.dailyRecommends` → `GetDailyRecommendsUseCase` 内部组合多个 Repository
- `SongDetailVM.albums` / `artists` → 分别注入 `AlbumRepository` 和 `ArtistRepository`

### 7.3 SourceItem 移除后的 MediaSource 调整

各平台 MediaSource 需要自主管理 ID→播放信息的映射：

| 平台 | 当前 SourceItem | 自主管理方案 |
|------|----------------|-------------|
| Android (MediaStore) | content URI | 从 `audio.id` 去掉前缀后拼接 content URI，无需额外存储 |
| iOS (MusicKit/MediaLibrary) | persistent ID | iOS 平台实现自行维护映射表 |
| JVM (FileSystem) | 文件路径 | `audio.id` 本身就是路径，无需额外存储 |
| Subsonic (Remote) | 服务端 track ID | `audio.id` 包含 track ID，直接构造请求 |

### 7.4 与 PlaylistRepository 的对称性

`lplaylist` 模块的 Repository 模式保持不变。`PlaylistDetailVM.getSongsFlow()` 中的 `LMedia.instance.mapByFlow<LAudio>()` 改为 `audioRepository.getAudios()`。

### 7.5 Sortable / SortManager

排序逻辑保留在 ViewModel 层（通过 `SortManager` + `doSortState`），不提取 UseCase。

### 7.6 PlayerAction 架构限制

`PlayerAction` 是 `sealed class`，不能通过构造函数注入。过渡方案：在 `defaultPlayerActionHandler` 中通过 Koin 的 `koinInject<AudioRepository>()` 内联获取。

架构上应重构为独立的 Player Service + ViewModel 模式（本次不做）。

### 7.7 MediaSource 接口的 expect/actual 问题

当前 `PlatformMediaSource` 是 `expect` 声明 + 各平台 `actual` 实现。接口定义移到 lmedia-domain 后，`expect` 声明也要移到 lmedia-domain。但各平台的 `actual` 实现仍在 lmedia-core。

需要确认 KMP 的 `expect`/`actual` 可以跨模块工作：`expect` 在 lmedia-domain，`actual` 在 lmedia-core（依赖关系：lmedia-core → lmedia-domain）。这应该是可行的。

### 7.8 `Snapshot.toComposeState()` 的处理

该扩展函数在 Snapshot.kt 中，引用了 `State`/`mutableStateOf`（Compose 依赖）。移到 domain 后会引入 Compose 依赖。

方案：移入 domain 时移除该函数，改为在 UI 层以扩展函数形式存在（放在 lmedia-ui 或 consumer 中）。

## 十、测试计划

> 这是本次重构的核心收益之一——重构前所有业务逻辑混在 ViewModel 中，
> 完全无法单测。重构后每层都可以独立测试。

### 10.1 测试层次架构

```
┌─────────────────────────────────────────────┐
│  Test Layer 3: ViewModel 集成测试             │
│  注入 Fake UseCase 验证 State/Event 输出      │
│  runTest + Turbine                            │
├─────────────────────────────────────────────┤
│  Test Layer 2: UseCase 单元测试               │
│  注入 Fake Repository 验证业务规则             │
│  runTest + JUnit Assert                      │
├─────────────────────────────────────────────┤
│  Test Layer 1: Repository 单元测试             │
│  in-memory Room 数据库                        │
│  Room JVM Test Runner                        │
├─────────────────────────────────────────────┤
│  Test Layer 0: Mapper 单元测试                │
│  Entity ↔ Domain 双向映射验证                 │
│  纯 JVM 测试，不依赖任何框架                   │
└─────────────────────────────────────────────┘
```

### 10.2 测试基础设施（新增文件）

所有 Fake 实现集中放在 `lmedia-domain` 模块的 `commonTest` 目录下：

```
lmedia/lmedia-domain/src/commonTest/kotlin/com/lalilu/lmedia/domain/
├── fake/
│   ├── FakeAudioRepository.kt
│   ├── FakeAlbumRepository.kt
│   ├── FakeArtistRepository.kt
│   ├── FakeGenreRepository.kt
│   ├── FakeFolderRepository.kt
│   └── FakeMediaSourceBindingRepository.kt
└── util/
    └── FakeEntityFactory.kt       ← 快速构造测试 LAudio/LAlbum 的工厂函数
```

```kotlin
// FakeAudioRepository.kt — 通用 Fake，所有 UseCase 测试共用
class FakeAudioRepository : AudioRepository {
    private val store = MutableStateFlow<List<LAudio>>(emptyList())

    fun seed(vararg audios: LAudio) { store.value = audios.toList() }

    override fun getAudios(): Flow<List<LAudio>> = store

    override fun getAudios(ids: List<String>): Flow<List<LAudio>> =
        store.mapLatest { list -> list.filter { it.idValue() in ids } }

    override fun getAudio(id: String): Flow<LAudio?> =
        store.mapLatest { list -> list.firstOrNull { it.idValue() == id } }
}

// FakeEntityFactory.kt — 快速构建测试数据
fun createAudio(
    id: String = "audio_test_$id",
    title: String = "Test Song $id",
    subtitle: String = "Test Artist"
) = LAudio(id = id, title = title, subtitle = subtitle)

fun createAlbum(
    id: String = "album_test_$id",
    title: String = "Test Album"
) = LAlbum(id = id, title = title)
```

#### 测试模块位置

| 测试内容 | 模块 | 测试目录 | 运行平台 |
|---------|------|---------|---------|
| Mapper | `lmedia-data` | `src/commonTest` | JVM（Room 需 JVM） |
| RepositoryImpl | `lmedia-data` | `src/commonTest` | JVM（Room in-memory） |
| UseCase | `lmedia-domain` | `src/commonTest` | JVM（纯 Kotlin） |
| ViewModel | 各 feature 模块 | `src/commonTest` | JVM（Fake UseCase） |
| Fake 实现 | `lmedia-domain` | `src/commonTest` | JVM（纯 Kotlin） |

### 10.3 Test Layer 0：Mapper 测试

目的：确保 Room Entity ↔ Domain Model 的双向映射无误。

测试用例（每个 Entity 类型一套）：

| 测试 | 验证 |
|------|------|
| `LAudioEntity → LAudio` 正向映射 | 所有字段正确映射，refs/sourceItem 被丢弃 |
| `LAudio → LAudioEntity` 逆向映射 | 所有字段恢复，extra 等 Map 字段正确 |
| 空值/默认值映射 | nullable 字段的 null → 默认值处理 |
| 关联关系映射 | entity 中的 albumId/artistId 正确映射 |

```kotlin
// AudioMapperTest.kt
class AudioMapperTest {
    @Test
    fun `toDomain strips refs and sourceItem`() {
        val entity = LAudioEntity(
            id = "audio_1",
            title = "Test",
            subtitle = "Artist"
        )
        // entity.link(someArtist)  — 即使 entity 有 refs
        val domain = entity.toDomain()  // domain.refs 不存在
        // 编译不通过：domain.refs 不存在 ✓
        assertEquals("audio_1", domain.id)
        assertEquals("Test", domain.title)
    }

    @Test
    fun `toEntity restores from domain`() {
        val domain = LAudio(id = "audio_1", title = "Test", subtitle = "Artist")
        val entity = domain.toEntity()
        assertEquals("audio_1", entity.id)
        assertEquals("Artist", entity.subtitle)
    }
}
```

### 10.4 Test Layer 1：Repository 测试

目的：验证 RepositoryImpl 的正确性，使用 in-memory Room 数据库。

因为 Room 的 `@Dao` 需要在 Activity 或 Room JVM Test 环境下运行，**约定 Reposiotry 测试放在现有 DAO 测试所在的 `composeApp/src/commonTest` 或 `lmedia-data/src/commonTest` 中**（取决于 lmedia-data 是否已配置 Room JVM 测试支持）。

```kotlin
// AudioRepositoryImplTest.kt
class AudioRepositoryImplTest {
    private var db: ILMediaDatabase? = null
    private var repo: AudioRepository? = null

    @BeforeTest
    fun setup() {
        db = createInMemoryDatabase()  // 使用 Room.inMemoryDatabaseBuilder
        repo = AudioRepositoryImpl(db!!.audioDao())
    }

    @AfterTest
    fun teardown() {
        db!!.close()
    }

    @Test
    fun `getAudio returns null for non-existent id`() = runTest {
        val result = repo!!.getAudio("nonexistent").first()
        assertNull(result)
    }

    @Test
    fun `getAudios returns seeded data`() = runTest {
        val audios = listOf(
            LAudio(id = "audio_1", title = "Song 1"),
            LAudio(id = "audio_2", title = "Song 2")
        )
        db!!.mediaDao().insert(Snapshot(audios = audios), "test_source")

        val result = repo!!.getAudios().first()
        assertEquals(2, result.size)
    }
}
```

DAO 和 in-memory Database 的 fixture 辅助工具：

```kotlin
// DatabaseTestFixture.kt
fun createInMemoryDatabase(): ILMediaDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, LMusicDatabase::class.java)
        .build()  // 实际的 LMusicDatabase 实现
}
```

### 10.5 Test Layer 2：UseCase 测试

**这是最有价值的测试层**——UseCase 直接对应业务规则，纯 JVM 可运行，不依赖任何 Android 框架。

#### SearchAudiosUseCaseTest

| 测试用例 | 输入 | 预期 |
|---------|------|------|
| 空关键词返回全部 | `keywords = []` | 返回所有数据 |
| 单关键词精确匹配 | `keywords = ["Rock"]` | 只返回包含 "Rock" 的歌曲 |
| 多关键词 AND 匹配 | `keywords = ["Rock", "Classic"]` | 两个都匹配才返回 |
| 大小写不敏感 | `keywords = ["rock"]` | 匹配到 "Rock" |
| 按 ID 列表筛选 | `ids = [id1, id2], keywords = []` | 只返回 ids 列表中的 |
| 空数据源 | 无数据 | 返回空列表 |

```kotlin
// SearchAudiosUseCaseTest.kt
class SearchAudiosUseCaseTest {
    private val repo = FakeAudioRepository()
    private val useCase = SearchAudiosUseCase(repo)

    @BeforeTest
    fun setup() {
        repo.seed(
            createAudio("1", title = "Rock Classic"),
            createAudio("2", title = "Pop Music"),
            createAudio("3", title = "Rock & Roll"),
            createAudio("4", title = "Jazz Standard")
        )
    }

    @Test
    fun `returns all when keywords empty`() = runTest {
        val result = useCase(keywords = emptyList()).first()
        assertEquals(4, result.size)
    }

    @Test
    fun `filters by single keyword`() = runTest {
        val result = useCase(keywords = listOf("Rock")).first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.title.contains("Rock") })
    }

    @Test
    fun `case insensitive`() = runTest {
        val result = useCase(keywords = listOf("rock")).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `AND filter with multiple keywords`() = runTest {
        val result = useCase(keywords = listOf("Rock", "Classic")).first()
        assertEquals(1, result.size)
        assertEquals("Rock Classic", result.first().title)
    }

    @Test
    fun `filters by id list`() = runTest {
        val result = useCase(
            ids = listOf("audio_1", "audio_2"),
            keywords = emptyList()
        ).first()
        assertEquals(2, result.size)
    }
}
```

#### GetDailyRecommendsUseCaseTest

| 测试用例 | 预期 |
|---------|------|
| 空数据源时不刷新 | `needsRefresh()` 返回 false |
| 有数据无推荐时刷新 | 生成 10 song + 2 album + 2 artist |
| 推荐列表非空时不过度刷新 | 已有推荐时 `needsRefresh()` 返回 false |
| 推荐列表包含所有实体类型 | 返回的 List<LItem> 包含 Audio/Album/Artist |

#### GetRelatedArtistsUseCaseTest

| 测试用例 | 预期 |
|---------|------|
| 歌手有共同歌曲 | 返回关联歌手列表 |
| 歌手无共同歌曲 | 返回空列表 |
| 排除自身 | 关联歌手中不包含自身 |

### 10.6 Test Layer 3：ViewModel 测试

ViewModel 变薄后，测试也更简单——只需要验证：
1. UseCase 被正确调用（参数验证）
2. UseCase 返回的结果被正确映射到 UiState

示例（使用 Turbine 验证 Flow 输出）：

```kotlin
// AlbumDetailVMTest.kt
class AlbumDetailVMTest {
    private val audioRepo = FakeAudioRepository()
    private val albumRepo = FakeAlbumRepository()
    private val searchUseCase = SearchAudiosUseCase(audioRepo)

    @Test
    fun `search updates filtered songs`() = runTest {
        albumRepo.seed(LAlbum(id = "album_1", title = "Test Album"))
        audioRepo.seed(
            createAudio("1", title = "Rock"),
            createAudio("2", title = "Pop")
        )

        val vm = AlbumDetailVM(
            albumId = "album_1",
            audioRepo = audioRepo,
            albumRepo = albumRepo,
            searchUseCase = searchUseCase
        )

        // 初始状态：返回所有歌曲
        // 触发搜索
        vm.intent(AlbumDetailAction.SearchFor("Rock"))

        // 验证 songs 已经筛选
        val songs = vm.songs.first()
        assertEquals(1, songs.size)
        assertEquals("Rock", songs.first().title)
    }
}
```

### 10.7 测试与各阶段的对应关系

| 阶段 | 实现内容 | 配套测试 | 测试文件数 |
|------|---------|---------|-----------|
| Phase 1 | lmedia-domain 模块 | Fake 实现 + EntityFactory | ~7 个 |
| Phase 2 | Room Entity + Mapper + Repository | MapperTest + RepositoryImplTest | ~10 个 |
| Phase 3 | UseCase | UseCaseTest（每 UseCase 一套） | ~3 个 |
| Phase 4 | 消费者改造 | ViewModelTest（可选，建议后续） | 0~6 个 |

测试开发应**与实现同步或优先**（测试先行）。

### 10.8 测试文件清单

```
lmedia/lmedia-domain/src/commonTest/kotlin/com/lalilu/lmedia/domain/
├── fake/
│   ├── FakeAudioRepository.kt           ← 新增
│   ├── FakeAlbumRepository.kt           ← 新增
│   ├── FakeArtistRepository.kt          ← 新增
│   ├── FakeGenreRepository.kt           ← 新增
│   ├── FakeFolderRepository.kt          ← 新增
│   └── FakeMediaSourceBindingRepository.kt ← 新增
├── util/
│   └── FakeEntityFactory.kt             ← 新增
└── usecase/
    ├── SearchAudiosUseCaseTest.kt       ← 新增
    ├── GetDailyRecommendsUseCaseTest.kt ← 新增
    └── GetRelatedArtistsUseCaseTest.kt  ← 新增

lmedia/lmedia-data/src/commonTest/kotlin/com/lalilu/lmedia/data/
├── mapper/
│   ├── AudioMapperTest.kt               ← 新增
│   ├── AlbumMapperTest.kt               ← 新增
│   ├── ArtistMapperTest.kt              ← 新增
│   ├── GenreMapperTest.kt               ← 新增
│   └── FolderMapperTest.kt              ← 新增
└── repository/
    ├── AudioRepositoryImplTest.kt       ← 新增
    ├── AlbumRepositoryImplTest.kt       ← 新增
    ├── ArtistRepositoryImplTest.kt      ← 新增
    ├── GenreRepositoryImplTest.kt       ← 新增
    └── FolderRepositoryImplTest.kt      ← 新增
```

---

## 八、执行顺序

```
Phase 1: lmedia-domain 模块创建 + Entity/核心接口迁移
  - 建模块 build.gradle
  - 迁移 Entity、MediaSource 接口、Snapshot
  - 调整 lmedia-core / lmedia-data 依赖
  - 创建 Fake 测试基础设施（~7 个文件）

Phase 2: Room Entity + Mapper + Repository 实现
  - 创建 LAudioEntity 等
  - 创建 Mapper
  - 调整 DAO 引用
  - 创建 RepositoryImpl
  - 注册 Koin
  - Mapper + Repository 单元测试（~10 个文件）

Phase 3: UseCase 实现
  - SearchAudiosUseCase
  - GetDailyRecommendsUseCase
  - GetRelatedArtistsUseCase
  - UseCase 单元测试（3 个文件，~13 个场景）

Phase 4: 消费者改造（按模块独立提交，每模块一次 commit）
  - lhome → lalbum → lartist → lhistory → lplaylist → lplayer

Phase 5: 收尾清理
  - 删除 Library/LMedia
  - 全局搜索清理
  - 构建验证
```

---

## 九、不做的事（明确排除）

- ❌ 不重构 PlayerAction 架构（独立 Service + ViewModel 是后续工作项）
- ❌ 不引入 Hilt / Compose DI 等新依赖
- ❌ 不改动 `lplaylist` 模块的 Repository 模式（已符合 Clean Architecture）
- ❌ 不涉及 UI 层的功能改动

## 十、Phase 6：MediaSource 领域接口迁移（额外完成）

### 目标

迁移所有平台 MediaSource 实现到 domain 模块定义的接口，删除旧接口和桥接代码。

### 改动内容

| 文件 | 操作 | 说明 |
|------|------|------|
| `domain.source.MediaSource` | 修改 | 原无 config，保持 domain 纯净 |
| `core.source.MediaSource` | 重写 | 改为 `interface MediaSource : DomainMediaSource`，加上 `config` 属性 |
| `core.source.MediaData` | 重写 | typealias = domain.source.MediaData |
| `core.source.MediaDataSource` | 重写 | typealias = domain.source.MediaDataSource |
| `entity.Snapshot.kt` | 删除 | 改用 domain.source.Snapshot |
| `PlatformMediaSource.kt` (core) | 重写 | 函数式注册 → 由 SharedModule 手动注册 |
| `MediaSourceBindingRepositoryImpl` | 重写 | 移除桥接层，直接注入 domain PlatformMediaSource |
| `SandboxFileSystemSource` (iOS) | 重写 | 使用 domain LAudio + Snapshot，移除 SourceItem |
| `JvmFileSystemSource` (JVM) | 重写 | 使用 domain LAudio + Snapshot |
| `MusicKitSource` (iOS) | 重写 | 使用 domain LAudio |
| `MediaLibrarySource` (iOS) | 重写 | 使用 domain LAudio |
| `SubsonicSource` (remote) | 重写 | 使用 domain LAudio + Metadata |
| `RemoteSource` (remote) | 重写 | 使用 domain LAudio，移除 SourceItem |
| `AVPlayerPlayback` (iOS) | 修改 | entity→domain LAudio 转换 |
| `PlayerViewModel` | 修改 | entity→domain LAudio 转换 |
| `SourceCard` / `SnapshotPreviewCard` (UI) | 修改 | entity→domain Snapshot |
| `LAudioFetcher` (coil) | 修改 | 使用 domain 类型 |
| `MediaSourceScreen` / `RemoteServerPanel` (UI) | 修改 | 使用 domain PlatformMediaSource |
| `MediaSourceUiManager` (全部平台) | 修改 | Content 扩展改为 domain MediaSource |

### 关键决策

- **`config` 保留在 core**：Domain `MediaSource` 不包含 config（UI 配置是跨层关注点），core `MediaSource` 继承了 domain 并加上 `config`
- **`MediaSourceConfig` 留在 core**：配置框架依赖 `KVContext`/`Saver` 等平台类型，不适合在纯 domain 模块
- **`@Single` 顶层函数不被 krouter KSP 处理**：`PlatformMediaSource` 注册移到了 `SharedModule` 手动 `single<PlatformMediaSource> { ... }`
- **`entity→domain LAudio` 转换**：Playback 层仍使用 entity LAudio（PlayableQueue），调用 domain `MediaDataSource` 前需转换

### 遗留问题

1. ✅ App 启动不闪退（PlatformMediaSource 注入修复）
2. ❌ 媒体数据源页面为空 — 部分 MediaSource 缺少 `@Single(binds = [MediaSource::class])` 注解，`getAll<MediaSource>()` 收集不到
   - `MusicKitSource` / `MediaLibrarySource` — object 类无 `@Single`，需加上
   - `RemoteSource` — `@Single(createdAtStart = true)` 无 binds
   - `SubsonicSource` — 使用 domain MediaSource binds，需对齐为 core MediaSource

---

## 十一、测试计划

> 这是本次重构的核心收益之一——重构前所有业务逻辑混在 ViewModel 中，
> 完全无法单测。重构后每层都可以独立测试。
