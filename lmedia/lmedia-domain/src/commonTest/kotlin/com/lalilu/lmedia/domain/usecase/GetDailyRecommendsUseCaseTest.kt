package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.fake.FakeAlbumRepository
import com.lalilu.lmedia.domain.fake.FakeArtistRepository
import com.lalilu.lmedia.domain.fake.FakeAudioRepository
import com.lalilu.lmedia.domain.fake.FakeDailyRecommendsStorage
import com.lalilu.lmedia.domain.util.createAlbum
import com.lalilu.lmedia.domain.util.createArtist
import com.lalilu.lmedia.domain.util.createAudio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetDailyRecommendsUseCaseTest {
    private val audioRepo = FakeAudioRepository()
    private val albumRepo = FakeAlbumRepository()
    private val artistRepo = FakeArtistRepository()
    private val storage = FakeDailyRecommendsStorage()
    private val useCase = GetDailyRecommendsUseCase(audioRepo, albumRepo, artistRepo, storage)

    @BeforeTest
    fun setup() {
        audioRepo.seed(
            createAudio("1", title = "Song 1"),
            createAudio("2", title = "Song 2"),
            createAudio("3", title = "Song 3"),
            createAudio("4", title = "Song 4"),
            createAudio("5", title = "Song 5"),
            createAudio("6", title = "Song 6"),
            createAudio("7", title = "Song 7"),
            createAudio("8", title = "Song 8"),
            createAudio("9", title = "Song 9"),
            createAudio("10", title = "Song 10"),
            createAudio("11", title = "Song 11"),
            createAudio("12", title = "Song 12")
        )
        albumRepo.seed(
            createAlbum("A1", title = "Album 1"),
            createAlbum("A2", title = "Album 2"),
            createAlbum("A3", title = "Album 3")
        )
        artistRepo.seed(
            createArtist("AR1", title = "Artist 1"),
            createArtist("AR2", title = "Artist 2")
        )
    }

    @AfterTest
    fun teardown() {
        audioRepo.clear()
        albumRepo.clear()
        artistRepo.clear()
    }

    @Test
    fun `needsRefresh returns true when storage is empty`() = runTest {
        val result = useCase.needsRefresh().first()
        assertTrue(result)
    }

    @Test
    fun `needsRefresh returns false when storage has data`() = runTest {
        storage.set(listOf("audio_1", "album_A1"))
        val result = useCase.needsRefresh().first()
        assertFalse(result)
    }

    @Test
    fun `refresh generates recommendations`() = runTest {
        useCase.refresh()
        val ids = storage.flow().first()
        assertFalse(ids.isEmpty())
        // Should contain 10 audios + 2 albums + 2 artists = 14 items
        assertEquals(14, ids.size)
    }

    @Test
    fun `get returns mapped items after refresh`() = runTest {
        useCase.refresh()
        val items = useCase.get().first()
        assertNotNull(items)
        assertFalse(items.isEmpty())
        // Items should include LItems (mix of audios, albums, artists)
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun `get returns empty when storage is empty`() = runTest {
        val items = useCase.get().first()
        assertTrue(items.isEmpty())
    }
}
