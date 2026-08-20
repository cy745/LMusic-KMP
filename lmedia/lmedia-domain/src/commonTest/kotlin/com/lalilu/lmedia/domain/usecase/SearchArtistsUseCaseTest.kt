package com.lalilu.lmedia.domain.usecase

import com.lalilu.lmedia.domain.fake.FakeArtistRepository
import com.lalilu.lmedia.domain.util.createArtist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchArtistsUseCaseTest {
    private val repo = FakeArtistRepository()
    private val useCase = SearchArtistsUseCase(repo)

    @BeforeTest
    fun setup() {
        repo.seed(
            createArtist("1", title = "周杰伦"),
            createArtist("2", title = "王力宏"),
            createArtist("3", title = "陶喆"),
            createArtist("4", title = "林俊杰")
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
        val result = useCase(keywords = listOf("周杰伦")).first()
        assertEquals(1, result.size)
        assertEquals("artist_1", result.first().id)
    }

    @Test
    fun `case insensitive`() = runTest {
        repo.clear()
        repo.seed(
            createArtist("1", title = "John Mayer"),
            createArtist("2", title = "Adele")
        )
        val result = useCase(keywords = listOf("john")).first()
        assertEquals(1, result.size)
    }

    @Test
    fun `AND filter with multiple keywords`() = runTest {
        repo.clear()
        repo.seed(
            createArtist("1", title = "Rock Star Singer"),
            createArtist("2", title = "Rock Only"),
            createArtist("3", title = "Pop Star Singer")
        )
        val result = useCase(keywords = listOf("Rock", "Singer")).first()
        assertEquals(1, result.size)
        assertEquals("artist_1", result.first().id)
    }

    @Test
    fun `matches subtitle`() = runTest {
        repo.clear()
        repo.seed(
            createArtist("1", title = "周杰伦", subtitle = "Jay Chou"),
            createArtist("2", title = "Other", subtitle = "Pop Singer")
        )
        val result = useCase(keywords = listOf("Jay")).first()
        assertEquals(1, result.size)
        assertEquals("artist_1", result.first().id)
    }

    @Test
    fun `returns empty when no match`() = runTest {
        val result = useCase(keywords = listOf("NonExistent")).first()
        assertTrue(result.isEmpty())
    }
}