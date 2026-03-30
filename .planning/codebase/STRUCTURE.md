# Codebase Structure

**Analysis Date:** 2026-03-30

## Directory Layout

```
LMusic-KMP/
├── .planning/                # Planning documentation
├── .github/                  # GitHub workflows
├── .gradle/                  # Gradle wrapper
├── .idea/                    # IntelliJ IDEA config
├── build/                    # Build output / old build
├── build-logic/              # Gradle convention plugins
├── common/                   # Base utilities library
├── composeApp/               # Application entry point
├── component/                 # Shared UI components
├── docs/                     # Documentation
├── gradle/                   # Gradle wrapper files
├── iosApp/                   # iOS native wrapper project
├── kotlin-js-store/           # JS/WASM store
├── lhome/                    # Home screen module
├── llyric/                   # Lyric parsing library
├── llyricview/               # Lyric display components
├── lmedia/                   # Media library management
│   ├── lmedia-client/        # Client for remote media
│   ├── lmedia-coil/          # Image loading integration
│   ├── lmedia-core/         # Core media entities
│   ├── lmedia-data/         # Database layer
│   ├── lmedia-server/       # LAN media server
│   └── lmedia-ui/           # Media source UI
├── lplayer/                  # Audio player core
│   ├── lib-decoder-flac/    # FLAC decoder plugin
│   └── libwrapper/          # Native wrapper library
├── lmedia-server/            # Standalone media server
├── thirdparty/               # Third-party integrations
│   ├── gridlayout-compose/   # Grid layout library
│   └── navigation3-ui/       # Navigation3 UI helpers
├── build.gradle.kts          # Root build config
├── settings.gradle.kts       # Project structure
└── gradle.properties         # Gradle properties
```

## Directory Purposes

### `composeApp/`

**Purpose:** Main application entry point

**Contains:**
- Platform-specific initialization
- App composable with navigation
- Koin dependency injection setup
- Image loading configuration
- Platform utilities (mouse back press, window management)

**Key Files:**
- `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/App.kt` - Main app composable
- `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/Koin.kt` - DI setup
- `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/Coil.kt` - Image loading
- `composeApp/src/desktopMain/kotlin/com/lalilu/lmusic/main.kt` - Desktop entry point
- `composeApp/src/iosMain/kotlin/com/lalilu/lmusic/MainViewController.kt` - iOS entry point

### `common/`

**Purpose:** Cross-platform utility library

**Contains:**
- KV storage abstractions
- Flow utilities
- Coroutine extensions
- Serialization helpers

**Key Files:**
- `common/src/commonMain/kotlin/com/lalilu/common/kv/` - Key-value storage
- `common/src/commonMain/kotlin/com/lalilu/common/flow/` - Flow extensions
- `common/src/commonMain/kotlin/com/lalilu/common/ext/` - Utility extensions

### `component/`

**Purpose:** Reusable UI components and navigation

**Contains:**
- Navigation components (`AppRouter`, `Screen`, `NavIntent`)
- Smart bar components
- Preview utilities
- Extensions

**Key Files:**
- `component/src/commonMain/kotlin/com/lalilu/navigation/AppRouter.kt` - Navigation router
- `component/src/commonMain/kotlin/com/lalilu/navigation/Screen.kt` - Screen interface
- `component/src/commonMain/kotlin/com/lalilu/navigation/smartbar/` - Smart bar UI

### `lplayer/`

**Purpose:** Audio playback engine

**Contains:**
- Playback interface and implementations
- Player singleton
- Player ViewModel
- Playback UI components

**Key Files:**
- `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/LPlayer.kt` - Player singleton
- `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/playback/Playback.kt` - Playback interface
- `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/viewmodel/PlayerViewModel.kt` - ViewModel
- `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/screen/PlayerScreen.kt` - Player UI

### `lhome/`

**Purpose:** Home screen and library browsing

**Contains:**
- Home screen with panels
- Songs list screen
- History screen
- Detail screens
- Home-specific components

**Key Files:**
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/HomeScreen.kt` - Home screen
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/songs/SongsScreen.kt` - Songs list
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/HistoryScreen.kt` - History
- `lhome/src/commonMain/kotlin/com/lalilu/lhome/LHomeModule.kt` - DI module

### `lmedia/`

**Purpose:** Media library management

**Submodules:**

**`lmedia-core/`** - Core entities and media sources
- `lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/entity/` - Domain entities
- `lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/source/` - Media source interfaces

**`lmedia-data/`** - Database layer
- `lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/database/` - Room DAOs
- `lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/LMedia.kt` - Repository
- Platform implementations at `lmedia-data/src/androidMain/`, `lmedia-data/src/iosMain/`, etc.

**`lmedia-ui/`** - Media source UI
- `lmedia-ui/src/commonMain/kotlin/com/lalilu/lmedia/screen/MediaSourceScreen.kt` - Source management UI

**`lmedia-client/`** - Remote media client (Subsonic)

**`lmedia-coil/`** - Coil image loading integration

**`lmedia-server/`** - LAN media server (DLNA/UPnP)

### `llyric/` and `llyricview/`

**Purpose:** Lyric parsing and display

**Contains:**
- `llyric/` - LRC/TTML parsing
- `llyricview/` - Scrolling lyric view component

### `thirdparty/`

**Purpose:** Third-party library integrations

**Contains:**
- `navigation3-ui/` - Navigation3 UI helpers
- `gridlayout-compose/` - Grid layout components

### `build-logic/`

**Purpose:** Gradle convention plugins

**Contains:**
- Custom Gradle plugins for consistent build configuration
- Kotlin multiplatform conventions
- Android conventions

## Key File Locations

### Entry Points

| Platform | File |
|----------|------|
| Android | `composeApp/src/androidMain/` (standard Android lifecycle) |
| iOS | `composeApp/src/iosMain/kotlin/com/lalilu/lmusic/MainViewController.kt` |
| Desktop | `composeApp/src/desktopMain/kotlin/com/lalilu/lmusic/main.kt` |
| Web | `composeApp/src/wasmJsMain/`, `composeApp/src/webMain/` |

### Configuration

| Config | Location |
|--------|----------|
| Root build | `build.gradle.kts` |
| Project settings | `settings.gradle.kts` |
| Dependencies | `gradle/libs.versions.toml` |
| Gradle properties | `gradle.properties` |

### Core Logic

| Purpose | Location |
|---------|----------|
| Player | `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/` |
| Media Library | `lmedia/lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/` |
| Database | `lmedia/lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/data/` |
| Navigation | `component/src/commonMain/kotlin/com/lalilu/navigation/` |
| DI Modules | Each module has `*Module.kt` (e.g., `LPlayerModule.kt`, `LHomeModule.kt`) |

## Naming Conventions

### Files

- **Kotlin source files:** PascalCase (`PlayerScreen.kt`, `LPlayer.kt`)
- **Build files:** kebab-case (`build.gradle.kts`, `settings.gradle.kts`)
- **Resources:** snake_case (`drawable_bg.xml`, `strings.xml`)

### Directories

- **Source sets:** lowercase (`commonMain`, `androidMain`, `iosMain`)
- **Package directories:** lowercase (`com/lalilu/lplayer/`)
- **Resource directories:** kebab-case (`values/`, `drawable-hdpi/`)

### Classes and Functions

- **Classes/Interfaces:** PascalCase (`LPlayer`, `Playback`, `LAudio`)
- **Functions:** camelCase (`updatePlaylist`, `skipToNext`)
- **Constants:** SCREAMING_SNAKE_CASE (`PLAY_MODE_SHUFFLE`)
- **Sealed classes:** PascalCase with descriptive names (`PlaybackState`, `NavIntent`)

### Annotations

- **Routing:** `@Destination(router = ["/route"])` for screens
- **DI:** `@Module`, `@ComponentScan`, `@Single`
- **Room:** `@Database`, `@Entity`, `@Dao`

## Module Dependencies

```
composeApp
    ├── common
    ├── component
    ├── lhome
    ├── lplayer
    └── lmedia/lmedia-*

component
    ├── common
    ├── thirdparty/navigation3-ui
    └── thirdparty/gridlayout-compose

lplayer
    ├── common
    ├── component
    ├── lmedia/lmedia-core
    ├── lmedia/lmedia-data
    └── llyricview

lhome
    ├── common
    ├── component
    ├── lplayer
    └── lmedia/lmedia-*

lmedia/lmedia-core
    └── common

lmedia/lmedia-data
    ├── common
    └── lmedia/lmedia-core

llyricview
    └── llyric
```

## Source Set Organization

Each Kotlin Multiplatform module follows this structure:

```
module/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/        # Shared code
    ├── androidMain/kotlin/       # Android-specific
    ├── androidUnitTest/kotlin/   # Android unit tests
    ├── iosMain/kotlin/           # iOS-specific
    ├── jvmMain/kotlin/           # JVM-specific (Desktop)
    ├── jvmTest/kotlin/           # JVM tests
    ├── webMain/kotlin/           # Web/JS-specific
    ├── wasmJsMain/kotlin/        # WASM-specific
    ├── commonTest/kotlin/         # Common tests
    └── desktopMain/kotlin/        # Desktop-specific (alias for jvmMain)
```

## Where to Add New Code

### New Feature Module

1. Create new module directory at root level
2. Add to `settings.gradle.kts`: `include(":moduleName")`
3. Create `moduleName/build.gradle.kts` with KMP configuration
4. Create source sets: `src/commonMain/kotlin/`, etc.
5. Add Koin module: `moduleName/src/commonMain/kotlin/ModuleNameModule.kt`

### New Screen

1. Add screen file in appropriate module:
   - `lhome/src/commonMain/kotlin/com/lalilu/lhome/screen/`
   - `lplayer/src/commonMain/kotlin/com/lalilu/lplayer/screen/`
   - `lmedia/lmedia-ui/src/commonMain/kotlin/com/lalilu/lmedia/screen/`
2. Annotate with `@Destination(router = ["/route"])`
3. Implement `Screen`, optionally `ScreenMetadataFactory`, `ScreenInfoFactory`
4. Add to sidebar in `composeApp/src/commonMain/kotlin/com/lalilu/lmusic/App.kt`

### New Component

1. Reusable UI: `component/src/commonMain/kotlin/com/lalilu/component/`
2. Domain-specific: appropriate module
3. Preview utilities: `component/src/commonMain/kotlin/com/lalilu/preview/`

### New Platform Implementation

1. Create `platformImpl` function in common source:
   ```kotlin
   expect fun platformPlayback(library: Library): Playback
   ```
2. Create actual implementation in platform source set:
   ```kotlin
   actual fun platformPlayback(library: Library): Playback = PlatformPlaybackImpl()
   ```

### New Entity

1. Define interface in `lmedia/lmedia-core/src/commonMain/kotlin/com/lalilu/lmedia/entity/`
2. Implement Room `@Entity` in `lmedia/lmedia-data/src/commonMain/kotlin/com/lalilu/lmedia/entity/`
3. Add DAO methods in appropriate DAO file
4. Register in `LMediaDatabase`

---

*Structure analysis: 2026-03-30*
