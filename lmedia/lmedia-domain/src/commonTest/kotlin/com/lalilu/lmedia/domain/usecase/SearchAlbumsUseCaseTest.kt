package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.fake.FakeAlbumRepository
import com.lalilu.lmedia.domain.util.createAlbum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAlbumsUseCaseTest {
    private val repo = FakeAlbumRepository()
    private val useCase = SearchAlbumsUseCase(repo)

    @BeforeTest
    fun setup() {
        repo.seed(
            createAlbum("1", title = "Rock Classics"),
            createAlbum("2", title = "Pop Hits"),
            createAlbum("3", title = "Jazz Sessions"),
            createAlbum("4", title = "Acoustic Rock")
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
        val result = useCase(keywords = listOf("Rock", "Acoustic")).first()
        assertEquals(1, result.size)
        assertEquals("Acoustic Rock", result.first().title)
    }

    @Test
    fun `matches subtitle`() = runTest {
        repo.clear()
        repo.seed(
            createAlbum("1", title = "Greatest", subtitle = "Rock Hits"),
            createAlbum("2", title = "Other", subtitle = "Pop Hits")
        )
        val result = useCase(keywords = listOf("Rock")).first()
        assertEquals(1, result.size)
        assertEquals("album_1", result.first().id)
    }

    @Test
    fun `returns empty when no match`() = runTest {
        val result = useCase(keywords = listOf("NonExistent")).first()
        assertTrue(result.isEmpty())
    }
}