# Codebase Concerns

**Analysis Date:** 2026-03-30

## Known Issues

### Playback Duration Issue on Android
- **Symptoms:** Duration value may still reflect the previous song when switching tracks
- **File:** `lplayer/src/androidMain/kotlin/com/lalilu/lplayer/playback/MPlayerPlayback.kt:317`
- **Code:**
  ```kotlin
  // TODO 此处获取到的duration仍然可能是上一首歌曲的时长
  ```
- **Impact:** UI may display incorrect duration when rapidly skipping tracks

### VLC Playback Duration Initialization
- **Symptoms:** On JVM startup, playback data initializes as null, causing duration updates to fail
- **File:** `lplayer/src/jvmMain/kotlin/com/lalilu/lplayer/playback/VLCPlayback.kt:145`
- **Code:**
  ```kotlin
  // TODO 启动时初始化播放数据为null，导致后续无法正常更新播放时长
  ```
- **Impact:** Duration tracking may not work correctly on JVM/desktop until a track plays

### Song Header Jumper Missing i18n
- **Symptoms:** Character categories are hardcoded in Chinese
- **File:** `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/dialog/SongsHeaderJumperDialog.kt:145-149`
- **Code:**
  ```kotlin
  // TODO 待完善多语言
  return when (category) {
      CharCategory.MATH_SYMBOL -> "数学符号"
      CharCategory.CURRENCY_SYMBOL -> "货币符号"
  ```
- **Impact:** Non-Chinese users cannot use this feature properly

### Navigation PopUtil Screen Comparison
- **Symptoms:** Screen comparison logic for popping back to a specific screen may be incorrect
- **File:** `component/src/commonMain/kotlin/com/lalilu/navigation/AppRouter.kt:127`
- **Code:**
  ```kotlin
  // TODO screen的比较需要确认是否正确
  ```
- **Impact:** Navigation may not pop to the correct screen when using `PopUtil` intent

### TextLayout Optimization Opportunity
- **Symptoms:** TextLayoutUtils uses custom offset calculation instead of internal TextLayoutResult methods
- **File:** `llyricview/src/commonMain/kotlin/com/lalilu/llyricview/utils/TextLayoutUtils.kt:70`
- **Code:**
  ```kotlin
  // TODO 待优化，可使用TextLayoutResult内方法简化获取数据逻辑
  ```
- **Impact:** Performance inefficiency in lyric rendering

---

## Technical Debt

### JVM QueueAction Implementations
- **All JVM queue operations are stubbed out with TODO()**
- **File:** `lplayer/src/jvmMain/kotlin/com/lalilu/lplayer/action/QueueAction.jvm.kt:1-16`
- **Code:**
  ```kotlin
  actual fun handlePlatformQueueAction(action: QueueAction) {
      when (action) {
          is QueueAction.AddToEnd -> TODO()
          is QueueAction.AddToNext -> TODO()
          // ... all 10 actions are TODO()
      }
  }
  ```
- **Impact:** Queue manipulation (add, remove, move, clear) does not work on JVM/desktop platform
- **Fix approach:** Implement actual queue management logic for VLCPlayer

### AbstractPlayback Start Logic
- **The `start` parameter in `updatePlaylist` is not implemented**
- **File:** `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/playback/AbstractPlayback.kt:133`
- **Code:**
  ```kotlin
  override suspend fun updatePlaylist(playlist: List<LItem>, startIndex: Int, start: Boolean) {
      updatePlaylist(playlist)
      skipTo(startIndex) // TODO start 逻辑实现
  }
  ```
- **Impact:** The `start` flag is ignored; behavior is identical to non-start variant

### Playback Interface Gap
- **The `skipTo` interface lacks `start` parameter**
- **File:** `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/playback/Playback.kt:17`
- **Code:**
  ```kotlin
  // TODO suspend fun skipTo(index: Int, start: Boolean)
  ```
- **Impact:** Cannot distinguish between track change with seek-to-start vs. seeking to saved position

### PlayerViewModel Initialization Logic
- **Playlist population logic is commented out**
- **File:** `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/viewmodel/PlayerViewModel.kt:44`
- **Code:**
  ```kotlin
  // TODO 待重构启动时填充播放列表的逻辑
  // LMedia.instance.whenReady { ... }
  ```
- **Impact:** App does not auto-populate playlist on startup

### Subsonic Cancel Functionality
- **Cancel action is not implemented**
- **File:** `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicSource.kt:92`
- **Code:**
  ```kotlin
  // TODO: 实现取消逻辑
  logger.i(messageString = "Cancel requested")
  ```
- **Impact:** Users cannot cancel ongoing Subsonic operations

### Android skipTo Index -1
- **Seeking to index -1 (previous position) is not implemented**
- **File:** `lplayer/src/androidMain/kotlin/com/lalilu/lplayer/playback/MPlayerPlayback.kt:210-222`
- **Code:**
  ```kotlin
  override suspend fun skipTo(index: Int) = runWithBrowser {
      if (index == -1) {
          // TODO
          // val item = browser.getItem(id)...
  ```
- **Impact:** Cannot seek to previous playback position

---

## Security Concerns

### Hardcoded Server Credentials
- **Sensitive connection details are hardcoded in source code**
- **File:** `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicConfig.kt:19-20`
- **Code:**
  ```kotlin
  data class SubsonicConfig(
      val url: String = "http://192.168.3.6:4533/rest/",
      val username: String = "qiu745",
  ```
- **Also:** `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicSource.kt:57,59`
- **Impact:** Private IP address and username are exposed in git history
- **Recommendation:** Move to environment variables or secure storage

### VLCJ Debug Logging
- **VLC logging is set to DEBUG unconditionally**
- **File:** `lplayer/src/jvmMain/kotlin/com/lalilu/lplayer/player/VLCPlayerLoader.kt:24`
- **Code:**
  ```kotlin
  System.setProperty("vlcj.log", "DEBUG")
  ```
- **Impact:** Verbose logging may leak sensitive information in production

---

## Performance Risks

### GlobalScope Usage in Navigation
- **AppRouter uses GlobalScope for emitting navigation intents**
- **File:** `component/src/commonMain/kotlin/com/lalilu/navigation/AppRouter.kt:157-163`
- **Code:**
  ```kotlin
  fun intent(intent: NavIntent) = GlobalScope.launch {
      sharedFlow.emit(intent)
  }
  ```
- **Impact:** Unbounded coroutine launches; potential memory leaks; no structured concurrency

### RunBlocking in Media Browser
- **Multiple `runBlocking` calls on IO dispatcher in callback contexts**
- **Files:**
  - `lplayer/src/androidMain/kotlin/com/lalilu/lplayer/service/LMediaDataSource.kt:53,60`
  - `lplayer/src/androidMain/kotlin/com/lalilu/lplayer/playback/MPlayerPlayback.kt:104,335`
  - `lplayer/src/androidMain/kotlin/com/lalilu/lplayer/extensions/MediaItemExt.kt:121,137`
- **Impact:** Can cause thread starvation and ANRs on Android; defeats coroutine benefits

### Unbounded SharedFlow in AppRouter
- **SharedFlow has no replay or extraBufferCapacity limit**
- **File:** `component/src/commonMain/kotlin/com/lalilu/navigation/AppRouter.kt:139`
- **Code:**
  ```kotlin
  private val sharedFlow = MutableSharedFlow<NavIntent>()
  ```
- **Impact:** Late collectors may miss navigation events; buffering unbounded

### Multiple @Single(createdAtStart = true) Singletons
- **Several services start eagerly, potentially slow startup**
- **Files:**
  - `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/LPlayer.kt:10`
  - `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicSource.kt:26`
  - `lmedia/lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/LMedia.kt:38`
  - `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/impl/KvSettingsSaver.kt:11`
- **Impact:** App initialization may be slow; all services start regardless of use

---

## Open Questions

### Koin Stability Analyzer Usage
- **Debug-only recompose logger is unconditionally configured**
- **File:** `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/Koin.kt:30`
- **Code:**
  ```kotlin
  ComposeStabilityAnalyzer.setLogger(DebugRecomposeLogger) // TODO 需要判断debug模式才开启
  ```
- **Question:** Should this only run in debug builds? Currently runs in all builds.

### Skipping Test Coverage for Subsonic
- **SubsonicSource cancel and refresh operations have no tests**
- **File:** `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicSource.kt`

### Deprecated Thirdparty Navigation3-UI
- **The `thirdparty/navigation3-ui` directory contains a custom implementation**
- **Question:** Is this vendored from androidx.navigation3? Does it track upstream changes?
- **Risk:** May become stale or incompatible with future Compose versions

### Alpha/Beta Dependencies
- **Several critical dependencies use pre-release versions**
- **In `gradle/libs.versions.toml`:**
  - `composeMultiplatform = "1.11.0-alpha01"` - Compose multiplatform alpha
  - `room3 = "3.0.0-alpha01"` - Room database alpha
  - `sqlite = "2.7.0-alpha01"` - SQLite alpha
  - `paging = "3.5.0-alpha01"` - Paging alpha
  - `kotlin-test = "2.3.20-RC3"` - Kotlin release candidate
- **Question:** When are stable versions expected? What breaking changes may occur?

### LPlayer Singleton Architecture
- **Heavy use of `LPlayer.instance` throughout codebase**
- **Pattern observed in 50+ files:**
  - `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/action/PlayerAction.kt`
  - `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/viewmodel/PlayerViewModel.kt`
  - `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/SongDetailScreen.kt`
- **Question:** Is dependency injection preferred over singleton access? Current approach complicates testing.

---

## Missing Critical Features

### No E2E Testing Infrastructure
- **No automated end-to-end tests for core user flows**
- **Test coverage limited to unit tests in `commonTest` source sets

### No Error Recovery Strategy
- **Exceptions in coroutine contexts are logged but not recovered**
- **Files:**
  - `lmedia/lmedia-client/src/commonMain/kotlin/com/lalilu/lmedia/source/subsonic/SubsonicSource.kt:37-39`
  - `lplayer/src/jvmMain/kotlin/com/lalilu/lplayer/playback/VLCPlayback.kt:152-155`
- **Impact:** Transient failures may leave app in inconsistent state

---

## Test Coverage Gaps

### Platform-Specific Code Not Tested
- **JVM queue actions have no tests (all TODO())**
- **File:** `lplayer/src/jvmMain/kotlin/com/lalilu/lplayer/action/QueueAction.jvm.kt`

### Integration Testing Gap
- **No integration tests for playback across platforms**
- **Only Android-specific playback (`MPlayerPlayback`) has callback-based testing patterns

---

*Concerns audit: 2026-03-30*
