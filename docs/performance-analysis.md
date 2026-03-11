# LMusic应用启动性能分析报告

## 一、概述

本报告分析了LMusic KMP音乐播放器的启动链条和依赖注入配置，找出启动耗时较长和曲库加载慢的原因。

## 二、应用启动流程

### 2.1 启动入口

```
MainApplication.onCreate()
    ├── startKoin() + koinSetup()
    │     ├── SharedModule (Settings, Json)
    │     ├── AppModule (@ComponentScan "com.lalilu.lmusic")
    │     ├── LHomeModule (@ComponentScan "com.lalilu.lhome")
    │     ├── LPlayerModule (@ComponentScan "com.lalilu.lplayer")
    │     └── 动态SPI加载 KModule
    │           ├── LMediaModule
    │           ├── LMediaClientModule
    │           ├── LMediaServerModule
    │           └── LMediaUiModule
    └── platformSetupCoil()
        ↓
MainActivity.onCreate()
    ├── enableEdgeToEdge()
    ├── FileKit.init()
    └── setContent { App() }
```

## 三、关键问题分析

### 3.1 问题一：启动时创建了过多实例

**标记为 `@Single(createdAtStart = true)` 的类：**

| 类名 | 文件位置 | 问题 |
|------|----------|------|
| `LMedia` | lmedia-core/LMedia.kt:12 | 立即启动所有MediaSource |
| `PlatformMediaSource` | lmedia-core/PlatformMediaSource.kt:17 | 创建所有平台媒体源 |
| `LPlayer` | lplayer/LPlayer.kt:10 | 依赖LMedia |
| `RemoteSource` | lmedia-client/RemoteSource.kt:25 | init()会立即尝试加载远程数据 |
| `SubsonicSource` | lmedia-client/SubsonicSource.kt:26 | 可能触发网络请求 |
| `RemoteServer` | lmedia-ui/RemoteServer.kt:17 | 服务端启动 |

### 3.2 问题二：LMedia初始化阻塞

**LMedia.kt (第12-21行):**
```kotlin
@Single(binds = [Library::class], createdAtStart = true)
class LMedia(platformSource: PlatformMediaSource) : Library() {
    override val snapshotStateFlow: StateFlow<Snapshot> =
        combine(
            flows = platformSource.sources.map { it.source() },  // 合并所有源
            transform = { it.combineToOne() }
        ).onEach { ... }
         .distinctUntilChangedBy { it.updateTime }
         .stateIn(coroutineScope, SharingStarted.Eagerly, Snapshot.Loading)  // 立即开始收集
}
```

**问题：**
- `SharingStarted.Eagerly` 表示立即开始收集数据流
- 每次启动都会立即扫描MediaStore和文件系统

### 3.3 问题三：PlatformMediaSource初始化

**PlatformMediaSource.kt (第17-24行):**
```kotlin
@Single(createdAtStart = true)
fun provideMediaSource(scope: Scope): PlatformMediaSource {
    val platformMediaSource = scope.provideMediaSources().sources
    val source = scope.getKoin().getAll<MediaSource>()
    return PlatformMediaSource(platformMediaSource + source)
        .apply { sources.forEach { it.init() } }  // 立即调用init()
}
```

**Android平台 (PlatformMediaSource.android.kt):**
```kotlin
actual fun Scope.provideMediaSources(): PlatformMediaSource = PlatformMediaSource.provide(
    ::MediaStoreSource.reverseInject(),    // 系统媒体库
    ::AndroidFileSystemSource.reverseInject()  // 本地文件系统
)
```

### 3.4 问题四：RemoteSource启动时尝试加载数据

**RemoteSource.kt (第108-110行):**
```kotlin
override fun init() {
    loadData(isInitialize = true)  // 启动时立即尝试加载
}
```

如果用户配置了RemoteServer，启动时会尝试网络连接，可能导致阻塞。

### 3.5 问题五：曲库无缓存机制

**分析：**
- 项目没有使用Room/SQLDelight持久化曲库数据
- 每次启动都需要重新扫描MediaStore
- MediaStoreScanner每次调用都执行完整的数据库查询

**MediaStoreScanner.kt (第62-70行):**
```kotlin
override fun scan(): Snapshot {
    val cursor = context.applicationContext.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection, BASE_SELECTOR, null, BASE_SORT_ORDER
    )
    // 每次都重新查询，没有缓存
    cursor?.close()
    return Snapshot.Empty
}
```

## 四、启动耗时原因总结

1. **同步阻塞**: 多个`createdAtStart = true`的类在主线程同步初始化
2. **依赖链长**: LPlayer → LMedia → PlatformMediaSource → MediaStoreSource/FileSystemSource
3. **网络阻塞**: RemoteSource启动时尝试连接远程服务器
4. **无缓存**: 每次启动都需要完整扫描媒体库
5. **SPI加载**: 动态加载多个KModule增加初始化开销

## 五、曲库加载慢的原因总结

1. **Eagerly收集**: `stateIn(coroutineScope, SharingStarted.Eagerly, ...)` 立即开始
2. **多数据源合并**: combine多个Flow增加开销
3. **重复扫描**: 没有缓存机制，每次都重新扫描
4. **同步Cursor**: MediaStore查询可能阻塞
5. **文件系统扫描**: AndroidFileSystemSource使用Taglib读取元数据

## 六、当前Flow合并方案的问题

### 6.1 现有架构

```kotlin
// LMedia.kt
override val snapshotStateFlow: StateFlow<Snapshot> =
    combine(
        flows = platformSource.sources.map { it.source() },
        transform = { it.combineToOne() }
    ).stateIn(coroutineScope, SharingStarted.Eagerly, Snapshot.Loading)
```

### 6.2 问题分析

1. **阻塞传播**: `combine` 是同步的，任一Flow阻塞会导致下游收不到数据
2. **无增量更新**: 每次都是全量数据
3. **无删除感知**: 数据源只返回"现在有哪些"，不知道"哪些被删了"
4. **状态丢失**: 无法区分"加载中"、"成功"、"失败"状态

---

# 架构优化方案（针对你的问题）

## 一、问题解答

### 1.1 多模块多数据源的数据合并

**推荐方案：分层合并**

```
┌─────────────────────────────────────────┐
│           Room Database (缓存层)          │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐  │
│  │ audios  │ │ albums  │ │ artists  │  │
│  └─────────┘ └─────────┘ └──────────┘  │
└─────────────────────────────────────────┘
                    ↑
                    │ 同步/异步写入
┌─────────────────────────────────────────┐
│         Repository (数据聚合层)          │
│  - 管理各数据源                          │
│  - 处理数据合并和冲突                     │
│  - 处理删除同步                          │
└─────────────────────────────────────────┘
                    ↑
        ┌───────────┼───────────┐
        ▼           ▼           ▼
┌───────────┐ ┌───────────┐ ┌───────────┐
│ MediaStore │ │ FileSystem│ │  Remote   │
│  Source    │ │  Source   │ │  Source   │
└───────────┘ └───────────┘ └───────────┘
```

### 1.2 加载状态判断

**建议为每个数据源维护独立状态：**

```kotlin
data class SourceState(
    val status: LoadStatus = LoadStatus.Idle,
    val lastUpdateTime: Long = 0,
    val error: Throwable? = null
)

enum class LoadStatus {
    Idle,       // 未加载
    Loading,    // 加载中
    Success,    // 加载成功
    Error       // 加载失败
}
```

**合并后的整体状态：**

```kotlin
data class LibraryState(
    val isLoading: Boolean = false,     // 任意源正在加载
    val isRefreshing: Boolean = false,  // 正在刷新
    val error: Throwable? = null,       // 首个错误
    val sourceStates: Map<String, SourceState> = emptyMap()
)
```

### 1.3 数据可用时回调

**方案：使用StateFlow + 回调**

```kotlin
class MediaRepository(
    private val dao: MusicDao,
    private val sources: List<MediaSource>
) {
    // 对外暴露的只读数据
    val audiosFlow: Flow<List<LAudio>> = dao.getAllAudios()
    val libraryState: StateFlow<LibraryState> = ...

    // 数据可用时的回调机制
    private val _onDataReady = MutableSharedFlow<List<LAudio>>()
    val onDataReady: Flow<List<LAudio>> = _onDataReady.asSharedFlow()

    suspend fun refresh() {
        sources.forEach { source ->
            launch { refreshSource(source) }
        }
    }

    private suspend fun refreshSource(source: MediaSource) {
        try {
            source.source().collect { snapshot ->
                // 增量更新到数据库
                val newIds = snapshot.audios.keys
                val oldIds = dao.getAllAudioIds().toSet()

                // 新增
                val toInsert = snapshot.audios.values.filter { it.id !in oldIds }
                dao.insertAudios(toInsert)

                // 删除 (旧ID不在新数据中)
                val toDelete = oldIds - newIds
                dao.deleteAudiosByIds(toDelete)

                // 触发回调
                _onDataReady.emit(snapshot.audios.values.toList())
            }
        } catch (e: Exception) {
            // 处理错误，更新状态
        }
    }
}
```

## 二、Room缓存 + 懒加载方案

### 2.1 整体架构

```
启动时:
  1. 立即从Room读取缓存数据 (显示历史数据)
  2. 后台启动各数据源的懒加载
  3. 数据源更新后增量写入Room
  4. Room写入后自动刷新UI
```

### 2.2 Room数据库设计

```kotlin
@Entity(tableName = "audios")
data class AudioEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val source: String,  // 数据源标识: "mediastore", "filesystem", "remote"
    val lastModified: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String,
    val art: String?,
    val source: String
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String
)
```

### 2.3 Repository实现

```kotlin
class MusicRepository(
    private val audioDao: AudioDao,
    private val sources: Lazy<List<MediaSource>>  // 懒加载数据源
) {
    // 1. 立即可用的缓存数据
    val cachedAudios: Flow<List<AudioEntity>> = audioDao.getAllAudios()

    // 2. 各数据源的状态
    private val _sourceStates = MutableStateFlow<Map<String, SourceState>>(emptyMap())
    val sourceStates: StateFlow<Map<String, SourceState>> = _sourceStates.asStateFlow()

    // 3. 懒加载刷新
    suspend fun refreshAllSources() {
        sources.get().forEach { source ->
            refreshSource(source)
        }
    }

    suspend fun refreshSource(source: MediaSource) {
        _sourceStates.update { it + (source.name to SourceState(LoadStatus.Loading)) }

        try {
            source.source().collect { snapshot ->
                // 同步到Room
                syncToDatabase(source.name, snapshot)

                _sourceStates.update {
                    it + (source.name to SourceState(
                        LoadStatus.Success,
                        lastUpdateTime = System.currentTimeMillis()
                    ))
                }
            }
        } catch (e: Exception) {
            _sourceStates.update {
                it + (source.name to SourceState(
                    LoadStatus.Error,
                    error = e
                ))
            }
        }
    }

    private suspend fun syncToDatabase(sourceName: String, snapshot: Snapshot) {
        // 获取当前数据库中该源的数据ID
        val oldIds = audioDao.getIdsBySource(sourceName).toSet()
        val newIds = snapshot.audios.keys

        // 计算需要删除的 (在DB中但不在新数据中)
        val toDelete = oldIds - newIds
        audioDao.deleteByIds(toDelete)

        // 计算需要插入的 (在新数据中但不在DB中)
        val toInsert = snapshot.audios.values
            .filter { it.id !in oldIds }
            .map { it.toEntity(sourceName) }
        audioDao.insertAll(toInsert)

        // 计算需要更新的 (两边都有但内容变了)
        val toUpdate = snapshot.audios.values
            .filter { it.id in oldIds }
            .map { it.toEntity(sourceName) }
        audioDao.updateAll(toUpdate)
    }
}
```

### 2.4 处理删除问题的关键

**核心思路：每次刷新时，对比新旧数据，计算差量**

```kotlin
/**
 * 处理删除的核心逻辑:
 * 1. 从数据库获取该数据源的所有歌曲ID
 * 2. 从数据源获取当前所有歌曲ID
 * 3. 计算差集: oldIds - newIds = 需要删除的
 * 4. 执行删除操作
 */
private suspend fun handleDeletions(sourceName: String, newIds: Set<String>) {
    val oldIds = audioDao.getIdsBySource(sourceName)
    val toDelete = oldIds - newIds

    if (toDelete.isNotEmpty()) {
        audioDao.deleteByIds(toDelete)
        // 也可以发送删除事件通知
        _onSongDeleted.emit(toDelete)
    }
}
```

### 2.5 懒加载启动流程

```kotlin
class LMedia(
    private val repository: MusicRepository  // 注入Repository
) : Library() {

    // 立即返回缓存数据
    override val snapshotStateFlow: StateFlow<Snapshot> =
        repository.cachedAudios
            .map { entities -> entities.toSnapshot() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, Snapshot.Loading)

    init {
        // 后台懒加载刷新
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000) // 延迟1秒启动，让UI先渲染
            repository.refreshAllSources()
        }
    }
}
```

## 三、加载状态管理

### 3.1 统一状态流

```kotlin
data class UnifiedLibraryState(
    // 总体状态
    val isInitialLoadComplete: Boolean = false,
    val isRefreshing: Boolean = false,

    // 各数据源状态
    val sourceStates: Map<String, SourceState> = emptyMap(),

    // 缓存数据
    val cachedCount: Int = 0,
    val lastFullRefreshTime: Long = 0
)

class LibraryStateManager(
    private val repository: MusicRepository
) {
    private val _state = MutableStateFlow(UnifiedLibraryState())
    val state: StateFlow<UnifiedLibraryState> = _state.asStateFlow()

    // 监听各数据源状态变化
    fun observeSourceStates() {
        repository.sourceStates.collect { sourceStates ->
            val anyLoading = sourceStates.values.any { it.status == LoadStatus.Loading }
            val allLoaded = sourceStates.values.all {
                it.status == LoadStatus.Success || it.status == LoadStatus.Idle
            }

            _state.update {
                it.copy(
                    isInitialLoadComplete = allLoaded,
                    isRefreshing = anyLoading,
                    sourceStates = sourceStates,
                    cachedCount = repository.getCachedCount()
                )
            }
        }
    }
}
```

### 3.2 UI层使用

```kotlin
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel
) {
    val libraryState by viewModel.libraryState.collectAsState()
    val songs by viewModel.songs.collectAsState()

    // 显示加载状态
    when {
        !libraryState.isInitialLoadComplete && songs.isEmpty() -> {
            // 首次加载，显示骨架屏
            LoadingSkeleton()
        }
        libraryState.isRefreshing -> {
            // 后台刷新中，显示原有数据 + 刷新指示器
            SongsList(songs = songs)
            RefreshIndicator()
        }
        else -> {
            SongsList(songs = songs)
        }
    }

    // 显示各数据源状态
    libraryState.sourceStates.forEach { (source, state) ->
        SourceStatusChip(source = source, status = state.status)
    }
}
```

## 四、优化建议总结

| 问题 | 解决方案 |
|------|----------|
| Flow阻塞导致下游无数据 | 改用Room作为缓存层，Flow从Room读取 |
| 无加载状态 | 每个数据源维护独立状态，合并为统一状态 |
| 数据可用时回调 | 通过SharedFlow发送数据就绪事件 |
| 歌曲删除同步 | 每次刷新时对比新旧ID，计算差量删除 |
| 启动慢 | 懒加载 + 缓存优先，启动立即显示历史数据 |

## 五、关键文件路径

- DI配置: `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/Koin.kt`
- 核心曲库: `lmedia/lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/LMedia.kt`
- 平台源: `lmedia/lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/PlatformMediaSource.kt`
- MediaStore扫描: `lmedia/lmedia-core/src/androidMain/kotlin/com/lalilu/lmedia/source/mediastore/MediaStoreScanner.kt`
- 播放器: `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/LPlayer.kt`
