# Testing Patterns

**Analysis Date:** 2026-03-30

## Test Framework

**Primary Framework:** kotlin-test
**Version:** 2.3.20-RC3 (per `kotlin-test`)
**Coroutines Testing:** kotlinx-coroutines-test (bundled with coroutines 1.10.2)

**Dependencies (per `lmedia-data/build.gradle.kts`):**
```kotlin
test.dependencies {
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
}
```

## Test Organization

### Directory Structure
```
src/
  commonTest/kotlin/              # Shared tests for all platforms
    com/lalilu/lmusic/
      ComposeAppCommonTest.kt     # Application-level tests
    com/lalilu/lmedia/
      data/database/              # Database DAO tests
        LMediaLAudioDaoTest.kt
        LMediaLArtistDaoTest.kt
        LMediaLAlbumDaoTest.kt
        LMediaLGenreDaoTest.kt
        LMediaLMediaDaoTest.kt
      entity/                     # Serialization tests
        LAudioSerializationTest.kt
        LArtistSerializationTest.kt
        SnapshotSerializationTest.kt
  androidUnitTest/kotlin/          # Android unit tests
    AndroidFibiTest.kt
  androidDeviceTest/kotlin/        # Instrumented Android tests
    androidx/navigation3/scene/
```

### Naming Conventions
- **Test classes:** `*Test.kt` suffix (e.g., `LMediaLAudioDaoTest.kt`)
- **Test methods:** `test*` prefix with descriptive name (e.g., `testInsertAndRetrieveAudio`, `testGetAudiosByArtist`)
- **Stub tests:** Backtick-enclosed names with spaces (e.g., `` `test 3rd element` ``)

### Test Class Pattern
```kotlin
class LMediaLAudioDaoTest {
    private val db = requireDatabase<LMediaDatabase>(forceMemory = false)
    private val audioDao = db.audioDao()
    private val artistDao = db.artistDao()

    @Test
    fun testInsertAndRetrieveAudio() = runTest {
        val audio = LAudio(
            id = "audio-1",
            title = "夜的第七章",
            subtitle = "专辑: 依然范特西",
            mediaSourceName = "local"
        )

        // 插入
        audioDao.insert(audio)

        // 查询所有
        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertTrue(allAudios.isNotEmpty())
    }
}
```

## Test Structure

### Suite Organization
- One test class per DAO or logical unit
- Multiple `@Test` methods per class covering CRUD operations and edge cases
- Helper methods extracted for common operations (e.g., `requireDatabase()`)

### Test Method Patterns
```kotlin
@Test
fun testUpdateAudio() = runTest {
    // Arrange
    val audio = LAudio(...)

    // Act
    audioDao.insert(audio)
    val updated = audio.copy(title = "新标题")
    audioDao.update(updated)

    // Assert
    val result = audioDao.getAudio("audio-2").firstOrNull()
    assertEquals("新标题", result?.title)
}
```

### Lifecycle
- **Setup:** Per-class instantiation of database and DAOs
- **No explicit teardown:** In-memory or temporary databases auto-clean
- **Coroutines:** All suspending tests wrapped in `runTest { }`

## Database Testing

### Test Database Setup
```kotlin
// From LMediaDatabase.kt
expect inline fun <reified T : RoomDatabase> requireDatabase(
    name: String = T::class.qualifiedName!!,
    forceMemory: Boolean = true
): T
```

### In-Memory vs Disk
- `forceMemory = false` uses disk-based database
- `forceMemory = true` (default) uses in-memory for isolation

### Room Schema Export
Schema exported to `schemas/` directory:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### Test Fixtures
**Inline fixture creation in tests:**
```kotlin
val audio = LAudio(
    id = "audio-1",
    title = "夜的第七章",
    subtitle = "专辑: 依然范特西",
    mediaSourceName = "local"
)
```

**No factory pattern observed** - fixtures created inline in each test

## Assertion Patterns

### Standard Assertions
```kotlin
import kotlin.test.*

assertEquals(expected, actual)
assertNotNull(value)
assertTrue(condition)
assertFalse(condition)
assertNull(value)
```

### Coroutine-Specific
```kotlin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.firstOrNull

@Test
fun testGetAllAudiosWithRelations() = runTest {
    val allAudios = audioDao.getAllAudio().firstOrNull()
    assertNotNull(allAudios)
    assertEquals(2, allAudios.size)
}
```

### Collection Assertions
```kotlin
assertTrue(allAudios.any { it.id == "audio-1" })
assertEquals(1, linkedArtists.size)
assertTrue(linkedSongs.any { it.id == "song-rel-1" })
```

## Mocking

**No mocking framework detected:**
- No MockK dependency in `libs.versions.toml`
- No mockito dependency
- Tests use real Room database instances
- Platform-specific tests use actual platform implementations

## Serialization Testing

Tests verify kotlinx.serialization works correctly across platforms:
```kotlin
class LAudioSerializationTest {
    @Test
    fun testSerializeAndDeserialize() {
        val audio = LAudio(...)
        val json = Json.encodeToString(audio)
        val decoded = Json.decodeFromString<LAudio>(json)
        assertEquals(audio, decoded)
    }
}
```

## Test Coverage

**No coverage enforcement detected:**
- No JaCoCo configuration
- No Kover (Kotlin coverage) plugin
- CI/CD workflow does not run coverage

## Run Commands

**Gradle test tasks:**
```bash
# Run all tests
./gradlew check

# Run tests for specific module
./gradlew :lmedia:lmedia-data:test

# Run tests with logging
./gradlew :lmedia:lmedia-data:test --info

# Android device tests
./gradlew :lmedia:lmedia-data:connectedAndroidTest
```

## CI/CD Testing

**From `.github/workflows/main.yml`:**
- CI runs `composeApp:assembleRelease` (build-focused, no explicit test task)
- No dedicated test job in CI pipeline
- No coverage reporting in CI

**Note:** Tests are primarily run locally during development

## Common Test Patterns

### Testing DAOs
```kotlin
// Insert -> Query -> Assert
audioDao.insert(audio)
val result = audioDao.getAudio("id").firstOrNull()
assertNotNull(result)
assertEquals(expectedTitle, result.title)

// Update -> Query -> Assert
audioDao.update(updated)
val updated = audioDao.getAudio("id").firstOrNull()
assertEquals(newTitle, updated.title)

// Delete -> Query -> AssertNull
audioDao.delete(audio)
val deleted = audioDao.getAudio("id").firstOrNull()
assertNull(deleted)
```

### Testing Relations
```kotlin
// Create entities
audioDao.insert(audio)
artistDao.insert(artist)
artistDao.insertRelation(listOf(CrossRefLAudioXLArtist(artist.id, audio.id)))

// Query and verify via ref<T>()
val result = audioDao.getAudio("id").firstOrNull()
val linkedArtists = result.ref<LArtist>()
assertEquals(1, linkedArtists.size)
```

### Testing Snapshots
```kotlin
val snapshot = listOf(
    LAudio(...),
    LAudio(...)
).buildSnapshot()

assertEquals(2, snapshot.audios.size)
assertEquals(1, snapshot.artists.size)

db.mediaDao().insert(snapshot)

val allArtists = artistDao.getAllArtist().firstOrNull()
assertEquals(1, allArtists.size)
```

## Platform-Specific Tests

### Android Unit Tests
```kotlin
class AndroidFibiTest {
    @Test
    fun testFibonacci() {
        assertEquals(2, fibonacci(3))
    }
}
```

### Android Device Tests (Instrumented)
Located in `androidDeviceTest/` for tests requiring Android runtime:
- Navigation scene tests
- UI lifecycle tests
- Compose screenshot tests

### Stub Tests
Some modules have placeholder tests:
```kotlin
class FibiTest {
    @Test
    fun `test 3rd element`() {
    }
}
```

## Best Practices Observed

1. **Isolate tests:** Each test creates its own data with unique IDs
2. **Readable assertions:** Chinese comments explain test steps
3. **Flow handling:** Use `firstOrNull()` to collect flow values in tests
4. **Relation testing:** Verify bidirectional relationships
5. **Snapshot testing:** Test batch operations with `buildSnapshot()`

---

*Testing analysis: 2026-03-30*
