package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.fake.FakeAudioRepository
import com.lalilu.lmedia.domain.util.createAudio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAudiosUseCaseTest {
    private val repo = FakeAudioRepository()
    private val useCase = SearchAudiosUseCase(repo)

    @BeforeTest
    fun setup() {
        repo.seed(
            createAudio("1", title = "Rock Classic"),
            createAudio("2", title = "Pop Music"),
            createAudio("3", title = "Rock & Roll"),
            createAudio("4", title = "Jazz Standard")
        )
    }

    @AfterTest
    fun teardown() {
        repo.clear()
    }

    @Test
    fun `returns all when keywords empty`() = runTest {
        val result = useCase(keywords = emptyList()).first()
        assertEquals(4, result.size)
    }

    @Test
    fun `filters by single keyword`() = runTest {
        val result = useCase(keywords = listOf("Rock")).first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.title.contains("Rock") })
    }

    @Test
    fun `case insensitive`() = runTest {
        val result = useCase(keywords = listOf("rock")).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `AND filter with multiple keywords`() = runTest {
        val result = useCase(keywords = listOf("Rock", "Classic")).first()
        assertEquals(1, result.size)
        assertEquals("Rock Classic", result.first().title)
    }

    @Test
    fun `filters by id list`() = runTest {
        val result = useCase(ids = listOf("audio_1", "audio_2"), keywords = emptyList()).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `filters by id list with keywords`() = runTest {
        val result = useCase(
            ids = listOf("audio_1", "audio_2"),
            keywords = listOf("Rock")
        ).first()
        assertEquals(1, result.size)
        assertEquals("audio_1", result.first().id)
    }

    @Test
    fun `returns empty when no match`() = runTest {
        val result = useCase(keywords = listOf("NonExistent")).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-existent ids return empty`() = runTest {
        val result = useCase(ids = listOf("audio_nonexistent")).first()
        assertTrue(result.isEmpty())
    }
}
