package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.buildSnapshot
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLMediaDaoTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val audioDao = db.audioDao()
    private val artistDao = db.artistDao()
    private val albumDao = db.albumDao()
    private val genreDao = db.genreDao()

    @Test
    fun testInsertSnapshot() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(
                id = "song-1", title = "夜的第七章", subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "依然范特西", genre = "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-2", title = "告白气球", subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "周杰伦的床边故事", genre = "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-3", title = "稻香", subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "依然范特西", genre = "流行"),
                mediaSourceName = "local"
            )
        ))

        assertEquals(3, snapshot.audios.size)
        assertEquals(2, snapshot.albums.size)
        assertTrue(snapshot.artists.size >= 1)
        assertEquals(1, snapshot.genres.size)

        db.mediaDao().insert(snapshot, "")

        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertTrue(allAudios.size >= 3)

        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertTrue(allArtists.any { it.title == "周杰伦" })

        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertTrue(allAlbums.any { it.title == "依然范特西" })

        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertTrue(allGenres.any { it.title == "流行" })
    }

    @Test
    fun testMultipleArtistsFromAudio() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(
                id = "song-multi", title = "夜曲", subtitle = "",
                metadata = Metadata(artist = "周杰伦/方文山", album = "十一月的萧邦", genre = "流行"),
                mediaSourceName = "local"
            )
        ))

        assertEquals(2, snapshot.artists.size)

        db.mediaDao().insert(snapshot, "")

        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertTrue(allArtists.size >= 2)
    }

    @Test
    fun testMultipleAlbums() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-a", title = "歌曲A", subtitle = "",
                metadata = Metadata(artist = "artist-a", album = "专辑A", genre = "流行"),
                mediaSourceName = "local"),
            LAudio(id = "song-b", title = "歌曲B", subtitle = "",
                metadata = Metadata(artist = "artist-b", album = "专辑B", genre = "摇滚"),
                mediaSourceName = "local")
        ))

        assertEquals(2, snapshot.albums.size)
        assertEquals(2, snapshot.genres.size)

        db.mediaDao().insert(snapshot, "")

        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertEquals(2, allAlbums.size)

        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertEquals(2, allGenres.size)
    }

    @Test
    fun testAudioRelationsThroughMediaDao() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-rel-1", title = "关联歌曲1", subtitle = "",
                metadata = Metadata(artist = "ArtistX", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-rel-2", title = "关联歌曲2", subtitle = "",
                metadata = Metadata(artist = "ArtistX", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "")

        // Verify album→audio relations via DAO query (use full entity IDs with prefix)
        val audiosByAlbum = albumDao.getAudiosByAlbum("album_AlbumX").firstOrNull()
        assertNotNull(audiosByAlbum)
        assertEquals(2, audiosByAlbum.size)
        assertTrue(audiosByAlbum.any { it.id == "song-rel-1" })
        assertTrue(audiosByAlbum.any { it.id == "song-rel-2" })

        // Verify artist→audio relations via DAO query
        val audiosByArtist = artistDao.getAudiosByArtist("artist_ArtistX").firstOrNull()
        assertNotNull(audiosByArtist)
        assertEquals(2, audiosByArtist.size)
    }

    @Test
    fun testGenreRelations() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-genre-1", title = "歌曲1", subtitle = "",
                metadata = Metadata(artist = "A", album = "Album1", genre = "Rock"),
                mediaSourceName = "local"),
            LAudio(id = "song-genre-2", title = "歌曲2", subtitle = "",
                metadata = Metadata(artist = "B", album = "Album2", genre = "Jazz"),
                mediaSourceName = "local"),
            LAudio(id = "song-genre-3", title = "歌曲3", subtitle = "",
                metadata = Metadata(artist = "C", album = "Album3", genre = "Rock"),
                mediaSourceName = "local")
        ))

        assertEquals(2, snapshot.genres.size)

        db.mediaDao().insert(snapshot, "")

        // Verify the cross_ref has entries via getAudiosByGenre
        val audiosByRock = genreDao.getAudiosByGenre("genre_Rock").firstOrNull()
        assertNotNull(audiosByRock)
        assertEquals(2, audiosByRock.size)
    }

    @Test
    fun testGetAudiosByArtist() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-artist-1", title = "歌曲1", subtitle = "",
                metadata = Metadata(artist = "ArtistY", album = "Album1", genre = "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-artist-2", title = "歌曲2", subtitle = "",
                metadata = Metadata(artist = "ArtistY", album = "Album2", genre = "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "")

        val audios = artistDao.getAudiosByArtist("artist_ArtistY").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByAlbum() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-album-1", title = "歌曲1", subtitle = "",
                metadata = Metadata(artist = "A", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-album-2", title = "歌曲2", subtitle = "",
                metadata = Metadata(artist = "B", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "")

        val audios = albumDao.getAudiosByAlbum("album_AlbumX").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByGenre() = runTest {
        val snapshot = buildSnapshot(listOf(
            LAudio(id = "song-g-1", title = "歌曲1", subtitle = "",
                metadata = Metadata(artist = "A", album = "Album1", genre = "Electronic"),
                mediaSourceName = "local"),
            LAudio(id = "song-g-2", title = "歌曲2", subtitle = "",
                metadata = Metadata(artist = "B", album = "Album2", genre = "Electronic"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "")

        val audios = genreDao.getAudiosByGenre("genre_Electronic").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }
}
