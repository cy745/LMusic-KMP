# Technology Stack

**Analysis Date:** 2026-03-30

## Project Type

**Kotlin Multiplatform (KMP) Music Player Application**
- Project name: `LMusic-KMP`
- Multi-module Gradle build with Kotlin DSL
- Feature preview: `TYPESAFE_PROJECT_ACCESSORS`

## Languages

**Primary:**
- Kotlin 2.3.10 (languageVersion 2.2, API version 2.2)
- KSP 2.3.6 for annotation processing

**Secondary:**
- Java 11 (Android source/target compatibility)
- Java 21 (JVM desktop toolchain, server module)

## Build System

**Gradle:**
- AGP (Android Gradle Plugin): 8.11.1
- Kotlin Gradle Plugin: 2.3.10 (bundled via version catalog)
- Gradle Toolchain Resolver: foojay-resolver-convention 1.0.0
- Shadow JAR plugin: 9.0.0 (for lmedia-server fat JAR)

**Key Gradle Configuration:**
- Configuration cache: disabled (known Kotlin bug KT-77732)
- CInterop commonization: enabled
- Non-transitive R class: enabled
- AndroidX: enabled

## Targets

| Target | Type | Min SDK / Toolchain |
|---|---|---|
| Android | Application (debug/release) | minSdk 23, targetSdk 36 |
| iOS | Framework (ARM64, Simulator ARM64) | Xcode detected |
| Desktop JVM | Application | JVM 21 |
| Web (WASM/JS) | Executable | browser, experimental |

## Modules

| Module | Purpose |
|---|---|
| `composeApp` | Main application entry point (Android, Desktop, Web, iOS) |
| `common` | Shared business logic, Ktor client, Room, Koin DI |
| `component` | Reusable Compose UI components |
| `lplayer` | Media playback layer (ExoPlayer, VLCJ, native) |
| `lplayer:libwrapper` | JNA wrapper for macOS native media controls |
| `lplayer:lib-decoder-flac` | FLAC audio decoder (Android NDK) |
| `lhome` | Home screen feature module |
| `llyric` | Lyrics parsing and fetching |
| `llyricview` | Lyrics display component |
| `lmedia:lmedia-core` | Media source abstraction, Ktor server, Ktorfit |
| `lmedia:lmedia-data` | Room 3 database, entity definitions |
| `lmedia:lmedia-ui` | Media-related UI components |
| `lmedia:lmedia-server` | Embedded Ktor HTTP server for media streaming |
| `lmedia:lmedia-client` | Ktor HTTP client for remote media access (Subsonic/Navidrome) |
| `lmedia:lmedia-coil` | Coil image loading integration |
| `lmedia-server` | Standalone JVM CLI server (Clikt) |
| `thirdparty:navigation3-ui` | Custom navigation library |
| `thirdparty:gridlayout-compose` | Custom grid layout |
| `build-logic` | Gradle convention plugins |

## UI Framework

**Jetpack Compose Multiplatform 1.11.0-alpha01:**
- `compose-ui`, `compose-runtime`, `compose-foundation`
- `compose-material` (Material 1)
- `compose-material3` / `compose-material3.adaptive` (adaptive layouts, 1.2.0-beta01)
- `compose-ui-backhandler`
- `compose-components-resources` (localization resources)
- `compose-ui-tooling-preview`
- Material Kolor: 4.0.4 (dynamic theming)
- Remix Icon KMP: 0.0.2 (icon library)
- QRCode Kotlin: 4.5.0 (QR code generation)
- Compose Sonner: 0.4.0 (toast notifications)

**Third-party UI:**
- `androidx.navigation3:navigation3-runtime` 1.1.0-alpha01
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.10.0-alpha03
- `org.jetbrains.androidx.navigationevent:navigationevent-compose` 1.0.0-rc02

## Networking

**HTTP Client (Ktor):**
- `ktor-client-core`: 3.3.0
- `ktor-client-content-negotiation`: 3.3.0
- `ktor-client-serialization`: 3.3.0
- `ktor-client-logging`: 3.3.0
- Platform engines: `ktor-client-okhttp` (Android/JVM), `ktor-client-darwin` (iOS), `ktor-client-js` (WASM)

**HTTP Server (Ktor):**
- `ktor-server-core`: 3.3.0
- `ktor-server-cors`: 3.3.0
- `ktor-server-cio`: 3.3.0 (embedded engine)
- `ktor-server-content-negotiation`: 3.3.0

**API Client:**
- `ktorfit-lib`: 2.7.2 (Retrofit-like for Ktor, compiler plugin 2.3.3)

**Serialization:**
- `kotlinx-serialization-json`: 1.9.0
- `ktor-serialization-kotlinx-json`: 3.3.0
- `kotlinx-datetime`: 0.7.1

## Database & Storage

**Local Database:**
- `androidx.room3:room3-common`: 3.0.0-alpha01
- `androidx.room3:room3-runtime`: 3.0.0-alpha01
- `androidx.room3:room3-paging`: 3.0.0-alpha01
- `androidx.sqlite:sqlite-bundled`: 2.7.0-alpha01 (bundled SQLite for all platforms)
- `androidx.sqlite:sqlite-web`: 2.7.0-alpha01 (WASM target)

**Paging:**
- `androidx.paging:paging-common`: 3.5.0-alpha01
- `androidx.paging:paging-runtime`: 3.5.0-alpha01
- `androidx.paging:paging-compose`: 3.5.0-alpha01

**Key-Value Settings:**
- `multiplatform-settings`: 1.3.0 (with coroutines, make-observable, no-arg, serialization bundles)

**File Access:**
- `filekit-core`: 0.12.0
- `filekit-dialogs`: 0.12.0
- `filekit-dialogs-compose`: 0.12.0
- `filekit-coil`: 0.12.0

## Media Playback

**Android:**
- `androidx.media3:media3-exoplayer`: 1.8.0
- `androidx.media3:media3-session`: 1.8.0
- `androidx.media3:media3-common`: 1.8.0

**Desktop (JVM):**
- `vlcj`: 4.11.0 (VLC bindings)
- `rococoa-cocoa`, `rococoa-contrib`, `rococoa-core`: 4d401915af (macOS AppKit bindings via Rococoa)
- JNA: 5.18.1 (native library loader)

**iOS:**
- MusicKit framework (via cinterop `MusicKitWrapper`)
- `native-lib-loader`: 2.5.0 (SCL native library loader)

**Audio Metadata:**
- `taglib` (via cinterop, `taglib-wasm` npm package 0.5.4 for WASM)
- Custom NDK module: `lplayer:lib-decoder-flac`

## Dependency Injection

**Koin 4:**
- `koin-core`: 4.1.1
- `koin-compose`: 4.1.1
- `koin-compose-viewmodel`: 4.1.1
- `koin-annotations` / `koin-ksp-compiler`: 2.3.0 (KSP-based DI code generation)

## State Management

**FlowMVI 3.2.1:**
- `flowmvi-core`
- `flowmvi-compose`
- `flowmvi-android`
- `flowmvi-savedstate`
- `flowmvi-debugger-plugin`

**Navigation:**
- KRouter 0.0.4 (`io.github.cy745.KRouter:core`)

## Image Loading

**Coil 3.3.0:**
- `coil-compose`
- `coil-network-ktor3` (Ktor-based HTTP engine)
- `coil-svg`
- `coil-gif`

## Logging & Observability

**Logging:**
- `kermit`: 2.0.8 (Touchlab multiplatform logger)
- `kotlin-logging`: 7.0.3 (Shuego logging)
- `logback-classic`: 1.5.18 (JVM desktop logging)
- Human-Readable: 1.12.0

**Stability Analysis:**
- `compose-stability-analyzer`: 0.6.0

## Cryptography

**From kotlincrypto version catalog (0.7.2):**
- `kotlincrypto.hash.md` (MD5 hashing, used for Subsonic password auth)

**SweetSPI:**
- `dev.whyoleg.sweetspi:sweetspi-runtime`: 0.1.3

## Documentation & Publishing

**Documentation:**
- Dokka 2.0.0 (Kotlin documentation generation)

**Maven Publishing:**
- Vanniktech Maven Publish 0.35.0 (`com.vanniktech.maven.publish.base`)

## CLI (lmedia-server)

**Clikt 5.0.3:**
- `com.github.ajalt.clikt:clikt`
- `com.github.ajalt.clikt:clikt-markdown`

## Other Utilities

- `androidx.core:core-ktx`: 1.17.0
- `androidx.activity:activity-compose`: 1.12.2
- `androidx.lifecycle:lifecycle-runtime-compose`: 2.9.5
- `androidx.lifecycle:lifecycle-viewmodel-compose`: 2.9.5
- `org.jetbrains.kotlinx:kotlinx-io-core`: 0.8.0
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`: 1.10.2
- `qrcode-kotlin`: 4.5.0
- `compose-sonner`: 0.4.0

---

*Stack analysis: 2026-03-30*
