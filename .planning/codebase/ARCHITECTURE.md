# Architecture

**Analysis Date:** 2026-03-30

## Pattern Overview

**Overall:** Kotlin Multiplatform (KMP) with MVVM + Clean Architecture

This is a cross-platform music player built with Kotlin Multiplatform, targeting Android, iOS, Web (WASM), and Desktop (JVM). The architecture emphasizes:

- **Shared business logic** across all platforms via common source sets
- **Platform-specific implementations** using `expect`/`actual` pattern
- **UI layer** built with Jetpack Compose Multiplatform
- **Dependency injection** via Koin
- **Database persistence** via Room3 with multi-platform support
- **Navigation** via custom KRouter + AndroidX Navigation3

## Layers

### 1. Core Domain Layer (`lmedia/lmedia-core`, `llyric`)

- **Purpose:** Define domain entities and core business interfaces
- **Location:** `lmedia/lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/`, `llyric/src/commonMain/kotlin/com/lalilu/llyric/`
- **Contains:**
  - Entity models (`LAudio`, `LAlbum`, `LArtist`, `LGenre`, `LFolder`)
  - Media source abstractions (`PlatformMediaSource`, `MediaSource`)
  - Tag reading interface (`Taglib`)
- **Depends on:** `common` (base utilities)
- **Used by:** All upper layers

### 2. Data Layer (`lmedia/lmedia-data`)

- **Purpose:** Database persistence and data access
- **Location:** `lmedia/lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/`
- **Contains:**
  - Room3 database (`LMediaDatabase`)
  - DAOs (`LAudioDao`, `LArtistDao`, `LAlbumDao`, `LGenreDao`, `LFolderDao`, `LHistoryDao`)
  - Repository (`LMedia`, `HistoryRepository`)
  - Type converters
- **Depends on:** `lmedia-core` (entities), Room3, Kotlinx Serialization
- **Used by:** `lplayer`, `lhome`, `lmedia-ui`

### 3. Player Layer (`lplayer`)

- **Purpose:** Audio playback engine
- **Location:** `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/`
- **Contains:**
  - Playback interface (`Playback`)
  - Player singleton (`LPlayer`)
  - Player ViewModel (`PlayerViewModel`)
  - Playback mode definitions
  - UI components (toolbar, seekbar, playlist, etc.)
- **Platform Implementations:**
  - Android: `MPlayerPlayback` (Media3/ExoPlayer) at `lplayer/src/androidMain/`
  - iOS: `AVPlayerPlayback` at `lplayer/src/iosMain/`
  - Desktop: `VLCJPlayback` at `lplayer/src/jvmMain/`
  - Web: `WebAudioPlayback` at `lplayer/src/webMain/`
- **Depends on:** `lmedia-data`, `llyricview`, `component`
- **Used by:** `composeApp`

### 4. UI Module Layer (`lhome`, `lmedia/lmedia-ui`, `component`)

- **Purpose:** Screen compositions and reusable UI components
- **Locations:**
  - Home: `lhome/src/commonMain/kotlin/com/lalilu/lhome/`
  - Media UI: `lmedia/lmedia-ui/src/commonMain/kotlin/com/lalilu/lmedia/`
  - Components: `component/src/commonMain/kotlin/com/lalilu/`
- **Contains:**
  - Screen composables (`HomeScreen`, `PlayerScreen`, `SongsScreen`, `HistoryScreen`)
  - Navigation setup (`AppRouter`, `Screen`, `NavIntent`)
  - Smart bar components
  - Panel components
- **Depends on:** `lplayer`, `lmedia-data`, `lmedia-core`
- **Used by:** `composeApp`

### 5. Application Layer (`composeApp`)

- **Purpose:** Application entry point and platform-specific setup
- **Location:** `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/`
- **Contains:**
  - `App.kt` - Main composable with navigation setup
  - `Koin.kt` - Dependency injection configuration
  - `Coil.kt` - Image loading setup
  - Screen implementations
  - Platform-specific utilities
- **Platform Entry Points:**
  - Android: `composeApp/src/androidMain/`
  - iOS: `composeApp/src/iosMain/` (`MainViewController.kt`)
  - Desktop: `composeApp/src/desktopMain/kotlin/com/lalilu/lmusic/main.kt`
  - Web: `composeApp/src/wasmJsMain/`, `composeApp/src/webMain/`

### 6. Utilities Layer (`common`)

- **Purpose:** Cross-platform utilities and extensions
- **Location:** `common/src/commonMain/kotlin/com/lalilu/common/`
- **Contains:**
  - KV storage (`KVSaver`, `KVItem`, `UpdatableKV`)
  - Coroutine extensions (`DispatchersExtent`)
  - Flow utilities (`UpdatableFlow`, `Resource`)
  - Paging extensions
  - Serialization helpers
- **Depends on:** None (base layer)
- **Used by:** All modules

## Data Flow

### Media Scanning Flow

```
Platform Source (MediaStore/MusicKit/FileSystem)
    -> MediaSource.scan()
    -> PlatformMediaSource.sources
    -> LMedia.startSourceBinding()
    -> LMediaDatabase.mediaDao().insert()
    -> Flow<List<LAudio>>
```

### Playback Flow

```
User Action (tap play)
    -> PlayerAction.*.action()
    -> LPlayer.instance.*
    -> PlatformPlayback (actual implementation)
    -> Native Player (ExoPlayer/AVPlayer/VLCJ/WebAudio)
    -> State updates via StateFlow
    -> Compose UI updates
```

### Navigation Flow

```
Screen Composable (@Destination("/route"))
    -> AppRouter.route("/route").push()
    -> NavIntent created
    -> Interceptors process intent
    -> NavHandler executes on NavBackStack
    -> NavDisplay renders new Scene
```

## State Management

**Primary:** Kotlin StateFlow + SharedFlow

- `StateFlow<T>` for UI state (playing, current item, playlist)
- `SharedFlow<T>` for one-time events (errors, navigation)
- `MutableState<T>` in Composables for local UI state

**Lifecycle Integration:**
- `LifecycleEventObserver` in ViewModels
- `bindToLifecycle()` extension for automatic lifecycle binding
- `LifecycleResumeEffect` for lifecycle-aware coroutine scopes

**Persistence:**
- Room3 for structured data (music library, history)
- `Settings` (Russhwolf) for key-value preferences
- `KVSaver` for typed key-value storage

## Key Abstractions

### MediaSource

- **Purpose:** Platform-specific media source interface
- **Examples:** `MusicKitSource` (iOS), `MediaStoreSource` (Android), `FileSystemSource` (Desktop)
- **Pattern:** Abstract factory with platform-specific implementations

### Library / LMedia

- **Purpose:** Unified access to media library
- **Examples:** `Library` abstract class, `LMedia` singleton
- **Pattern:** Repository pattern with Flow-based API

### Playback

- **Purpose:** Audio playback interface
- **Examples:** `Playback` interface, `LPlayer` singleton
- **Pattern:** Strategy pattern with platform implementations via `expect`/`actual`

### Screen

- **Purpose:** Navigation screen definition
- **Examples:** `HomeScreen`, `PlayerScreen`, `SongsScreen`
- **Pattern:** Interface + Composable + KRouter annotations

## Entry Points

### Android

- **Location:** `composeApp/src/androidMain/`
- **Trigger:** Standard Android activity lifecycle
- **Responsibilities:** Initialize Media3, configure notifications

### iOS

- **Location:** `composeApp/src/iosMain/kotlin/com/lalilu/lmusic/MainViewController.kt`
- **Trigger:** iOS App launch via UIApplicationMain
- **Responsibilities:** Initialize MusicKit, configure audio session

### Desktop

- **Location:** `composeApp/src/desktopMain/kotlin/com/lalilu/lmusic/main.kt`
- **Trigger:** JVM main method
- **Responsibilities:** Window management, system tray, VLCJ initialization

### Web

- **Location:** `composeApp/src/wasmJsMain/`, `composeApp/src/webMain/`
- **Trigger:** WASM/JS load
- **Responsibilities:** Web Audio API initialization

## Error Handling

**Strategy:** Result type + Flow-based error propagation

**Patterns:**
- `runCatching { }` for try-catch with Result
- `SharedFlow<Throwable>` for playback errors
- `ExceptionScreen` for UI-level error display
- Logger integration via Kermit

## Cross-Cutting Concerns

**Logging:** Kermit (co.touchlab) with:
- `MemoryLogWriter` for in-memory log storage
- `DebugRecomposeLogger` for Compose stability analysis
- Platform-specific log writers

**Validation:** Not centralized; each module handles its own validation

**Authentication:** Not applicable (local music player)

**Dependency Injection:** Koin with:
- `@Single` annotations for singletons
- `@ComponentScan` for auto-registration
- `@Module` for custom DI configurations
- KSP for compile-time code generation

**Code Generation:**
- Koin Annotations via KSP
- Room3 via KSP
- KRouter routes via KSP

---

*Architecture analysis: 2026-03-30*
