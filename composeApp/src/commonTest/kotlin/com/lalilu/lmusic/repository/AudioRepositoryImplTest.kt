package com.lalilu.lmusic.repository

import com.lalilu.lmedia.data.database.ILMediaDatabase
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.data.mapper.toEntity
import com.lalilu.lmedia.data.repository.AudioRepositoryImpl
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioRepositoryImplTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private lateinit var repo: AudioRepository

    @BeforeTest
    fun setup() {
        repo = AudioRepositoryImpl(db)
    }

    @AfterTest
    fun teardown() {
        // Clean up test data
    }

    @Test
    fun `getAudio returns null for non-existent id`() = runTest {
        val result = repo.getAudio("nonexistent_id").firstOrNull()
        assertNull(result)
    }

    @Test
    fun `getAudios by non-existent ids returns empty`() = runTest {
        val result = repo.getAudios(listOf("nonexistent_1", "nonexistent_2")).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `insert and retrieve audio via repository`() = runTest {
        val audio = LAudio(
            id = "repo_test_audio_1",
            title = "Repository Test Song",
            subtitle = "Test Artist",
            mediaSourceName = "test_source",
            metadata = Metadata(title = "Meta"),
            extra = mapOf("track" to "1"),
            available = true
        )

        // Insert directly via DAO (Repository is read-only)
        db.audioDao().insert(audio.toEntity())

        // Verify via Repository
        val result = repo.getAudio("repo_test_audio_1").firstOrNull()
        assertNotNull(result)
        assertEquals("Repository Test Song", result.title)
        assertEquals("Test Artist", result.subtitle)
        assertEquals("test_source", result.mediaSourceName)
        assertEquals(mapOf("track" to "1"), result.extra)
        assertEquals(true, result.available)
    }

    @Test
    fun `getAudios returns all seeded data`() = runTest {
        val audios = listOf(
            LAudio(id = "repo_list_1", title = "Song 1", subtitle = "A1", mediaSourceName = "test"),
            LAudio(id = "repo_list_2", title = "Song 2", subtitle = "A2", mediaSourceName = "test"),
            LAudio(id = "repo_list_3", title = "Song 3", subtitle = "A3", mediaSourceName = "test")
        )
        db.audioDao().insertAll(audios.map { it.toEntity() })

        val result = repo.getAudios().first()
        assertTrue(result.size >= 3)
        assertTrue(result.any { it.id == "repo_list_1" })
        assertTrue(result.any { it.id == "repo_list_2" })
    }

    @Test
    fun `getAudios with ids returns filtered results`() = runTest {
        val audios = listOf(
            LAudio(id = "repo_filter_1", title = "Keep", subtitle = "", mediaSourceName = "test"),
            LAudio(id = "repo_filter_2", title = "Keep", subtitle = "", mediaSourceName = "test"),
            LAudio(id = "repo_filter_3", title = "Skip", subtitle = "", mediaSourceName = "test")
        )
        db.audioDao().insertAll(audios.map { it.toEntity() })

        val result = repo.getAudios(listOf("repo_filter_1", "repo_filter_3")).first()
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "repo_filter_1" })
        assertTrue(result.any { it.id == "repo_filter_3" })
    }

    @Test
    fun `getAudios with empty ids returns empty list`() = runTest {
        db.audioDao().insert(LAudio(id = "repo_empty_ids", title = "T", subtitle = "", mediaSourceName = "test").toEntity())

        val result = repo.getAudios(emptyList()).first()
        assertTrue(result.isEmpty())
    }
}
