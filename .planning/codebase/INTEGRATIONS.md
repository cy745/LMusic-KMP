# External Integrations

**Analysis Date:** 2026-03-30

## APIs & External Services

**Music Streaming (User-Provided):**
- Subsonic / Navidrome API
  - Protocol: HTTP/HTTPS REST API via `SubsonicSource` in `lmedia/lmedia-client`
  - Implementation: Ktor client + Ktorfit 2.7.2
  - Auth: MD5 password hashing via `kotlincrypto.hash.md` (Subsonic salt-based auth)
  - Endpoints consumed: music browsing, lyrics, streaming, artwork
  - User-configurable URL, username, password (stored encrypted in `multiplatform-settings`)

**Embedded HTTP Servers:**
- LMedia local streaming server
  - Implemented in: `lmedia/lmedia-server` (Ktor CIO engine, embedded)
  - Serves local media files over HTTP to the app itself (localhost)
  - Used on Android and Desktop to stream local files
- Standalone CLI server: `lmedia-server` module
  - Entry: `lmedia-server/src/main/kotlin/com/lalilu/lmedia/Main.kt`
  - CLI: Clikt 5.0.3, configurable port (default 7799) and file path
  - Serves local filesystem media via `JvmFileSystemSource`

## Data Storage

**Local Database:**
- SQLite (bundled, platform-native)
  - Client: `androidx.room3:room3` 3.0.0-alpha01
  - Schema location: `lmedia/lmedia-data/schemas/`
  - Compiler: KSP (`room3-compiler`)
  - Tables: Songs, albums, artists, playlists, playback history, media sources
  - Platforms: Android (bundled SQLite), iOS (bundled), Desktop JVM (bundled), Web (sqlite-web)

**File Storage:**
- Local filesystem via FileKit 0.12.0
  - `filekit-dialogs`, `filekit-dialogs-compose` for file/folder picking
  - No cloud storage integration

**Settings Storage:**
- `multiplatform-settings` 1.3.0 (encrypted key-value store)
  - Stores: Subsonic credentials, playback preferences, theme, navigation state

## Authentication & Identity

**Music Source Auth:**
- Subsonic/Navidrome: username + password (MD5 hashed, stored in settings)
- iOS MusicKit: Apple Music authorization (system prompt)

## Platform-Specific Integrations

**Android:**
- Media3 ExoPlayer: `androidx.media3:media3-exoplayer` 1.8.0 (audio decoding and playback)
- MediaSession: `androidx.media3:media3-session` 1.8.0 (system media controls, notifications)
- Android NDK: FLAC decoder via `lplayer:lib-decoder-flac` (C/C++ NDK module)
- Activity Result API: via `androidx.activity:activity-compose`

**iOS:**
- MusicKit framework (via cinterop `MusicKitWrapper`): access to Apple Music library
- Native media playback via MusicKit
- Taglib via cinterop for audio metadata reading

**Desktop (macOS/Linux/Windows):**
- VLCJ 4.11.0: libVLC bindings for cross-platform media playback
- Rococoa: macOS AppKit bindings (`rococoa-cocoa`, `rococoa-contrib`, `rococoa-core`)
- JNA 5.18.1: native library loading
- JNA Platform: 5.18.1

**Web (WASM):**
- `taglib-wasm` npm package 0.5.4: audio metadata reading in browser
- `kotlinx-browser`: browser API access
- SQLite Web: `androidx.sqlite:sqlite-web` 2.7.0-alpha01

## Image Loading & Caching

**Coil 3.3.0:**
- `coil-compose`: Compose integration
- `coil-network-ktor3`: Ktor HTTP engine for image loading
- `coil-svg`: SVG rendering support
- `coil-gif`: animated GIF support
- Custom fetchers: `MusicKitItemFetcher` in `lmedia/lmedia-coil`

## Monitoring & Observability

**Logging:**
- Kermit 2.0.8 (multiplatform structured logging, Touchlab)
- Logback Classic 1.5.18 (JVM desktop)
- Logcat on Android (via Kermit)

**Stability:**
- Compose Stability Analyzer 0.6.0 (`skydoves/compose-stability-analyzer`)

**No external APM or crash reporting detected**

## CI/CD & Deployment

**Android:**
- Debug APK built via Gradle (Android Gradle Plugin 8.11.1)
- Release APK: minified + shrunk, signed with keystore (via `keystore.properties`)
- ProGuard rules: `composeApp/proguard-android.pro`, `composeApp/proguard-desktop.pro`

**iOS:**
- Framework built via `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
- Integrated into Xcode project at `iosApp/iosApp.xcodeproj`

**Desktop:**
- Native distributions: DMG (macOS), MSI, DEB (Linux) via Compose Desktop
- Shadow JAR: `lmedia-server` standalone fat JAR (includes all dependencies)

**No external CI/CD services detected (GitHub Actions, etc.)**

## Environment Configuration

**No embedded secrets or API keys in the codebase.**
- `.env` files: not tracked or referenced in code
- Subsonic credentials: stored at runtime in `multiplatform-settings` (encrypted)
- Android keystore: external `keystore.properties` file (not committed)

## Third-Party Repositories

**Maven Repositories:**
- Google Maven (AndroidX, Android, Google)
- Maven Central
- JitPack (`https://jitpack.io`) — for `rococoa` fork by cy745
- KotlinCrypto version catalog (`org.kotlincrypto:version-catalog:0.7.2`)

---

*Integration audit: 2026-03-30*
