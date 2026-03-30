# Coding Conventions

**Analysis Date:** 2026-03-30

## Language and Version

**Primary Language:** Kotlin
**Kotlin Version:** 2.2.0 (Kotlin 2.3.10 per gradle, API/language 2.2)
**Target Platforms:** Android, iOS, Desktop (JVM), Web (WASM/JS)

## Naming Conventions

### Files
- **Source files:** PascalCase matching class name (e.g., `LMediaDatabase.kt`, `PlayerScreen.kt`)
- **Test files:** PascalCase with `Test` suffix (e.g., `LMediaLAudioDaoTest.kt`)
- **Platform-specific sources:** Use directory naming convention (`androidMain`, `iosMain`, `jvmMain`, `wasmJsMain`, `desktopMain`)
- **Common sources:** `commonMain` for shared code

### Packages
- **Base package:** `com.lalilu.*`
- **Module packages:** `com.lalilu.lmedia.*`, `com.lalilu.lplayer.*`, `com.lalilu.lmusic.*`, etc.
- **Components:** `com.lalilu.component.*`
- **Database:** `com.lalilu.lmedia.data.database.*`
- **Entities:** `com.lalilu.lmedia.entity.*`

### Classes
- **Entity classes:** Prefix with `L` (e.g., `LAudio`, `LArtist`, `LAlbum`, `LGenre`, `LHistory`)
- **DAO interfaces:** Suffix with `Dao` (e.g., `LAudioDao`, `LArtistDao`)
- **Database classes:** Suffix with `Database` (e.g., `LMediaDatabase`)
- **Screen classes:** Suffix with `Screen` (e.g., `PlayerScreen`)
- **Module classes:** Suffix with `Module` (e.g., `LHomeModule`, `LPlayerModule`)
- **ViewModel classes:** Suffix with `ViewModel` (e.g., `PlayerViewModel`)

### Functions and Methods
- **Naming:** camelCase (e.g., `getAudio()`, `insert()`, `getAllArtist()`, `mapBy()`)
- **Database operations:** Standard CRUD naming (`insert`, `update`, `delete`, `getAll*`, `get*`)
- **Flow return:** Methods returning `Flow` use generic names without `Flow` suffix (e.g., `getAudio(id: String): Flow<LAudio?>`)
- **Extension functions:** camelCase, often with descriptive verbs (e.g., `retrieve()`, `bindToLifecycle()`)
- **Coroutine builders in tests:** Use `runTest` block for suspending test functions

### Variables and Parameters
- **Naming:** camelCase (e.g., `contentId`, `parentId`, `startTime`)
- **Private properties:** Often `camelCase` with underscore prefix for backing fields
- **Constants:** SCREAMING_SNAKE_CASE in object/companion (rare in codebase)

### Types
- **Type aliases:** Used sparingly
- **Generic types:** Single uppercase letter (e.g., `T`, `I`) or descriptive (e.g., `reified T`)
- **Collections:** Standard library types (`Flow`, `List`, `Map`)

## Code Style

### Indentation and Formatting
- **Standard:** 4 spaces (Kotlin default)
- **No explicit formatter config:** Project relies on IntelliJ IDEA default Kotlin formatting
- **Line length:** No enforced limit observed

### Import Organization
Order observed:
1. Kotlin standard library (`kotlin.*`, `kotlinx.*`)
2. Android/Compose/Jetbrains imports (`androidx.*`, `org.jetbrains.*`)
3. Third-party libraries (Koin, Room, Coil, etc.)
4. Internal project imports (`com.lalilu.*`)
5. Companion object imports (grouped separately)

### Annotations
- **Experimental APIs:** `@OptIn(ExperimentalCoroutinesApi::class)` or `@OptIn(ExperimentalWasmDsl::class)`
- **Room:** `@Dao`, `@Entity`, `@Database`, `@Query`, `@Insert`, `@Update`, `@Delete`, `@TypeConverter`, `@ColumnInfo`
- **Koin:** `@Single` for singleton provision
- **Compose:** `@Composable`, `@Stable`, standard Compose annotations
- **Navigation:** `@Destination` for KRouter routes

### Braces and Blocks
- **Standard Kotlin style:** Open brace on same line
- **Single-expression functions:** Use expression body (e.g., `fun foo() = bar()`)
- **Companion objects:** Located at bottom of class

## Documentation Standards

### KDoc Usage
**Observed patterns:**
- Entity classes have KDoc describing the entity and parameters
- Public APIs have KDoc for complex components
- Android/Compose library files retain Apache 2.0 headers

**Example from `LHistory.kt`:**
```kotlin
/**
 * 播放历史记录实体
 *
 * @param id 主键，自增
 * @param contentId 内容的唯一标识符（对应音频ID）
 * @param contentTitle 内容标题
 * @param parentId 父级ID（如所属专辑/文件夹ID）
 * @param parentTitle 父级标题
 * @param duration 播放时长，-1L 表示预保存记录（会被清理），0 为正常值
 * @param repeatCount 重复播放次数
 * @param startTime 开始播放的时间戳
 */
@Entity(tableName = "m_history")
data class LHistory(...)
```

### Comments
- **Chinese comments:** Common in entity descriptions and test assertions
- **Inline comments:** Used sparingly for non-obvious logic
- **TODO comments:** None observed in current code

### License Headers
- **AGPL v3:** Used on entity classes and some core files
- **Apache 2.0:** Retained on third-party/ported code (e.g., AnchoredDraggable)

## Git Conventions

### Branch Naming
From `CONTRIBUTING.md`:

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<issue-id>-<description>` | `feature/123-add-playlist` |
| Bugfix | `bugfix/<issue-id>-<description>` | `bugfix/456-fix-crash` |
| Hotfix | `hotfix/<issue-id>-<description>` | `hotfix/789-urgent-fix` |

### Commit Messages
**Format:** [Conventional Commits](https://www.conventionalcommits.org/)

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code formatting (no functionality change)
- `refactor` - Refactoring (not feature or fix)
- `test` - Test-related
- `chore` - Build process or tooling changes

**Examples:**
```bash
git commit -m 'feat(player): add shuffle mode'
git commit -m 'fix(lyric): fix timestamp parsing error'
git commit -m 'docs: update README'
```

## Multiplatform Conventions

### Source Set Structure
```
src/
  commonMain/kotlin/     # Shared code for all platforms
  androidMain/kotlin/     # Android-specific
  iosMain/kotlin/         # iOS-specific
  jvmMain/kotlin/         # JVM/Desktop-specific
  wasmJsMain/kotlin/      # WebAssembly/JS-specific
  desktopMain/kotlin/     # Desktop-specific
  commonTest/kotlin/      # Shared tests
  androidUnitTest/kotlin/ # Android unit tests
  androidDeviceTest/      # Android instrumented tests
```

### Expect/Actual Pattern
- Platform-specific implementations use `expect fun`/`expect object` in common
- Actual implementations in platform-specific source sets (e.g., `Platform.kt`, `LMediaDatabaseConstructor`)

### Module Dependencies
- **lmedia-core:** Core media entities and interfaces
- **lmedia-data:** Room database, DAOs, repositories
- **lmedia-ui:** UI components for media
- **lplayer:** Player logic and controls
- **composeApp:** Main application entry point

## Linter and Analysis

**No explicit linter configuration detected:**
- No `.editorconfig`
- No `detekt.yml`
- No `ktlint.yml`

**Static analysis tool present:**
- `stability-analyzer` plugin for Compose stability analysis

**Style enforcement:**
- Relies on Kotlin compiler defaults and IntelliJ IDEA settings
- `CONTRIBUTING.md` states "use Kotlin official code style"

---

*Convention analysis: 2026-03-30*
