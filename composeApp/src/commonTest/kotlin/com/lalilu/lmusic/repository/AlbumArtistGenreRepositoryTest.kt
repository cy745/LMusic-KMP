package com.lalilu.lmusic.repository

import com.lalilu.lmedia.data.database.LAlbumDao
import com.lalilu.lmedia.data.database.LArtistDao
import com.lalilu.lmedia.data.database.LGenreDao
import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.mapper.toDomain
import com.lalilu.lmedia.data.repository.AlbumRepositoryImpl
import com.lalilu.lmedia.data.repository.ArtistRepositoryImpl
import com.lalilu.lmedia.data.repository.GenreRepositoryImpl
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumArtistGenreRepositoryTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val albumRepo = AlbumRepositoryImpl(db.albumDao())
    private val artistRepo = ArtistRepositoryImpl(db.artistDao())
    private val genreRepo = GenreRepositoryImpl(db.genreDao())
    private val albumDao: LAlbumDao = db.albumDao()
    private val artistDao: LArtistDao = db.artistDao()
    private val genreDao: LGenreDao = db.genreDao()

    @Test
    fun `album repository insert and query`() = runTest {
        val entity = LAlbumEntity(id = "repo_album_1", title = "Test Album", subtitle = "Sub", extra = null)
        albumDao.insert(entity)

        val result = albumRepo.getAlbum("repo_album_1").firstOrNull()
        assertNotNull(result)
        assertEquals("Test Album", result.title)
    }

    @Test
    fun `album repository getAlbums returns all`() = runTest {
        albumDao.insertAll(listOf(
            LAlbumEntity(id = "repo_alb_a", title = "A", subtitle = ""),
            LAlbumEntity(id = "repo_alb_b", title = "B", subtitle = "")
        ))

        val result = albumRepo.getAlbums().first()
        assertTrue(result.size >= 2)
        assertTrue(result.any { it.id == "repo_alb_a" })
    }

    @Test
    fun `album repository getAlbums with ids`() = runTest {
        albumDao.insertAll(listOf(
            LAlbumEntity(id = "repo_alb_1", title = "One", subtitle = ""),
            LAlbumEntity(id = "repo_alb_2", title = "Two", subtitle = ""),
            LAlbumEntity(id = "repo_alb_3", title = "Three", subtitle = "")
        ))

        val result = albumRepo.getAlbums(listOf("repo_alb_1", "repo_alb_3")).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `artist repository insert and query`() = runTest {
        val entity = LArtistEntity(id = "repo_artist_1", title = "Test Artist", subtitle = "Bio")
        artistDao.insert(entity)

        val result = artistRepo.getArtist("repo_artist_1").firstOrNull()
        assertNotNull(result)
        assertEquals("Test Artist", result.title)
    }

    @Test
    fun `artist repository getArtists returns all`() = runTest {
        artistDao.insertAll(listOf(
            LArtistEntity(id = "repo_art_a", title = "A", subtitle = ""),
            LArtistEntity(id = "repo_art_b", title = "B", subtitle = "")
        ))

        val result = artistRepo.getArtists().first()
        assertTrue(result.size >= 2)
    }

    @Test
    fun `artist repository getArtists with ids`() = runTest {
        artistDao.insertAll(listOf(
            LArtistEntity(id = "repo_art_1", title = "One", subtitle = ""),
            LArtistEntity(id = "repo_art_2", title = "Two", subtitle = ""),
            LArtistEntity(id = "repo_art_3", title = "Three", subtitle = "")
        ))

        val result = artistRepo.getArtists(listOf("repo_art_1", "repo_art_3")).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `genre repository insert and query`() = runTest {
        val entity = LGenreEntity(id = "repo_genre_1", title = "Rock", subtitle = "")
        genreDao.insert(entity)

        val result = genreRepo.getGenre("repo_genre_1").firstOrNull()
        assertNotNull(result)
        assertEquals("Rock", result.title)
    }

    @Test
    fun `genre repository getGenres returns all`() = runTest {
        genreDao.insertAll(listOf(
            LGenreEntity(id = "repo_gen_a", title = "Rock", subtitle = ""),
            LGenreEntity(id = "repo_gen_b", title = "Pop", subtitle = "")
        ))

        val result = genreRepo.getGenres().first()
        assertTrue(result.size >= 2)
    }

    @Test
    fun `getNonExistentReturnsNull`() = runTest {
        assertNull(albumRepo.getAlbum("nonexistent_album").firstOrNull())
        assertNull(artistRepo.getArtist("nonexistent_artist").firstOrNull())
        assertNull(genreRepo.getGenre("nonexistent_genre").firstOrNull())
    }
}
