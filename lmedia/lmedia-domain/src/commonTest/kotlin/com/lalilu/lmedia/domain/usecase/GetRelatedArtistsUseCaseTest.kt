package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.fake.FakeArtistRepository
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.util.createArtist
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetRelatedArtistsUseCaseTest {
    private val artistRepo = FakeArtistRepository()
    private val useCase = GetRelatedArtistsUseCase(artistRepo)

    @BeforeTest
    fun setup() {
        artistRepo.seed(
            createArtist("main", title = "Main Artist"),
            createArtist("related1", title = "Related Artist 1"),
            createArtist("related2", title = "Related Artist 2"),
            createArtist("solo", title = "Solo Artist")
        )
    }

    @AfterTest
    fun teardown() {
        artistRepo.clear()
    }

    @Test
    fun `returns empty when artist not found`() = runTest {
        val result = useCase("nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty when artist has no songs`() = runTest {
        // Main artist has no audio IDs associated
        val result = useCase("artist_main")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns related artists through shared songs`() = runTest {
        // Main Artist has 2 songs
        artistRepo.seedWithAudioIds("artist_main", listOf("audio_1", "audio_2"))
        // Song 1 also has related1
        artistRepo.seedWithAudioIds("artist_related1", listOf("audio_1"))
        // Song 2 also has related2
        artistRepo.seedWithAudioIds("artist_related2", listOf("audio_2"))

        val result = useCase("artist_main")
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "artist_related1" })
        assertTrue(result.any { it.id == "artist_related2" })
    }

    @Test
    fun `excludes self from related artists`() = runTest {
        // Main Artist also appears on its own songs (should be excluded)
        artistRepo.seedWithAudioIds("artist_main", listOf("audio_1"))
        artistRepo.seedWithAudioIds("artist_related1", listOf("audio_1"))

        val result = useCase("artist_main")
        assertEquals(1, result.size)
        assertEquals("artist_related1", result.first().id)
    }

    @Test
    fun `deduplicates artists appearing on multiple songs`() = runTest {
        // related1 appears on both song 1 and song 2
        artistRepo.seedWithAudioIds("artist_main", listOf("audio_1", "audio_2"))
        artistRepo.seedWithAudioIds("artist_related1", listOf("audio_1", "audio_2"))

        val result = useCase("artist_main")
        assertEquals(1, result.size)
        assertEquals("artist_related1", result.first().id)
    }
}
