package com.lalilu.lmedia.domain.fake

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeAudioRepositoryTest {

    @Test
    fun `seed and getAudios`() = runTest {
        val repo = FakeAudioRepository()
        repo.seed(LAudio(id = "audio_1", title = "Song 1"))

        val result = repo.getAudios().first()
        assertEquals(1, result.size)
        assertEquals("Song 1", result.first().title)
    }

    @Test
    fun `getAudio by id`() = runTest {
        val repo = FakeAudioRepository()
        repo.seed(
            LAudio(id = "audio_1", title = "Song 1"),
            LAudio(id = "audio_2", title = "Song 2")
        )

        val result = repo.getAudio("audio_1").firstOrNull()
        assertNotNull(result)
        assertEquals("Song 1", result.title)

        assertNull(repo.getAudio("nonexistent").firstOrNull())
    }

    @Test
    fun `getAudios by ids`() = runTest {
        val repo = FakeAudioRepository()
        repo.seed(
            LAudio(id = "audio_a", title = "A"),
            LAudio(id = "audio_b", title = "B"),
            LAudio(id = "audio_c", title = "C")
        )

        val result = repo.getAudios(listOf("audio_a", "audio_c")).first()
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "audio_a" })
        assertTrue(result.any { it.id == "audio_c" })
    }

    @Test
    fun `clear resets state`() = runTest {
        val repo = FakeAudioRepository()
        repo.seed(LAudio(id = "audio_1"))
        assertEquals(1, repo.getAudios().first().size)

        repo.clear()
        assertTrue(repo.getAudios().first().isEmpty())
    }
}
