package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.mapper.toEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val snapshot = Snapshot(listOf(
            LAudio(
                id = "song-1", title = "夜的第七章", subtitle = "",
                extra = audioExtra("周杰伦", "依然范特西", "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-2", title = "告白气球", subtitle = "",
                extra = audioExtra("周杰伦", "周杰伦的床边故事", "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-3", title = "稻香", subtitle = "",
                extra = audioExtra("周杰伦", "依然范特西", "流行"),
                mediaSourceName = "local"
            )
        ))

        assertEquals(3, snapshot.audios.size)
        db.mediaDao().insert(snapshot, "local")

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
        val snapshot = Snapshot(listOf(
            LAudio(
                id = "song-multi", title = "夜曲", subtitle = "",
                extra = audioExtra("周杰伦/方文山", "十一月的萧邦", "流行"),
                mediaSourceName = "local"
            )
        ))

        db.mediaDao().insert(snapshot, "local")

        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertTrue(allArtists.size >= 2)
    }

    @Test
    fun testMultipleAlbums() = runTest {
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-a", title = "歌曲A", subtitle = "",
                extra = audioExtra("artist-a", "专辑A", "流行"),
                mediaSourceName = "local"),
            LAudio(id = "song-b", title = "歌曲B", subtitle = "",
                extra = audioExtra("artist-b", "专辑B", "摇滚"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertEquals(2, allAlbums.size)

        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertEquals(2, allGenres.size)
    }

    @Test
    fun testAudioRelationsThroughMediaDao() = runTest {
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-rel-1", title = "关联歌曲1", subtitle = "",
                extra = audioExtra("ArtistX", "AlbumX", "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-rel-2", title = "关联歌曲2", subtitle = "",
                extra = audioExtra("ArtistX", "AlbumX", "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

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
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-genre-1", title = "歌曲1", subtitle = "",
                extra = audioExtra("A", "Album1", "Rock"),
                mediaSourceName = "local"),
            LAudio(id = "song-genre-2", title = "歌曲2", subtitle = "",
                extra = audioExtra("B", "Album2", "Jazz"),
                mediaSourceName = "local"),
            LAudio(id = "song-genre-3", title = "歌曲3", subtitle = "",
                extra = audioExtra("C", "Album3", "Rock"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

        // Verify the cross_ref has entries via getAudiosByGenre
        val audiosByRock = genreDao.getAudiosByGenre("genre_Rock").firstOrNull()
        assertNotNull(audiosByRock)
        assertEquals(2, audiosByRock.size)
    }

    @Test
    fun testGetAudiosByArtist() = runTest {
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-artist-1", title = "歌曲1", subtitle = "",
                extra = audioExtra("ArtistY", "Album1", "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-artist-2", title = "歌曲2", subtitle = "",
                extra = audioExtra("ArtistY", "Album2", "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

        val audios = artistDao.getAudiosByArtist("artist_ArtistY").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByAlbum() = runTest {
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-album-1", title = "歌曲1", subtitle = "",
                extra = audioExtra("A", "AlbumX", "Pop"),
                mediaSourceName = "local"),
            LAudio(id = "song-album-2", title = "歌曲2", subtitle = "",
                extra = audioExtra("B", "AlbumX", "Pop"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

        val audios = albumDao.getAudiosByAlbum("album_AlbumX").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByGenre() = runTest {
        val snapshot = Snapshot(listOf(
            LAudio(id = "song-g-1", title = "歌曲1", subtitle = "",
                extra = audioExtra("A", "Album1", "Electronic"),
                mediaSourceName = "local"),
            LAudio(id = "song-g-2", title = "歌曲2", subtitle = "",
                extra = audioExtra("B", "Album2", "Electronic"),
                mediaSourceName = "local")
        ))

        db.mediaDao().insert(snapshot, "local")

        val audios = genreDao.getAudiosByGenre("genre_Electronic").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun missingAudioIsMarkedUnavailableOnlyInsideCommittedSource() = runTest {
        db.mediaDao().insert(
            Snapshot(listOf(
                sourceAudio("available-a-1", "source-a"),
                sourceAudio("available-a-2", "source-a"),
            )),
            "source-a",
        )
        db.mediaDao().insert(
            Snapshot(listOf(sourceAudio("available-b-1", "source-b"))),
            "source-b",
        )

        db.mediaDao().insert(
            Snapshot(listOf(sourceAudio("available-a-1", "source-a"))),
            "source-a",
        )

        assertEquals(true, audioDao.getAudio("available-a-1").firstOrNull()?.available)
        assertEquals(false, audioDao.getAudio("available-a-2").firstOrNull()?.available)
        assertEquals(true, audioDao.getAudio("available-b-1").firstOrNull()?.available)
    }

    @Test
    fun audioIdCannotBeTakenOverByAnotherSource() = runTest {
        db.mediaDao().insert(
            Snapshot(listOf(sourceAudio("source-collision", "source-owner"))),
            "source-owner",
        )

        assertFailsWith<IllegalArgumentException> {
            db.mediaDao().insert(
                Snapshot(listOf(sourceAudio("source-collision", "source-other"))),
                "source-other",
            )
        }

        assertEquals(
            "source-owner",
            audioDao.getAudio("source-collision").firstOrNull()?.mediaSourceName,
        )
    }

    @Test
    fun clearUnavailableRemovesOrphanRelationsAndDerivedEntities() = runTest {
        db.mediaDao().insert(
            Snapshot(listOf(
                sourceAudio(
                    id = "clear-current",
                    source = "clear-source",
                    artist = "Shared Artist",
                    album = "Shared Album",
                    genre = "Shared Genre",
                ),
                sourceAudio(
                    id = "clear-shared-missing",
                    source = "clear-source",
                    artist = "Shared Artist",
                    album = "Shared Album",
                    genre = "Shared Genre",
                ),
                sourceAudio(
                    id = "clear-orphan-missing",
                    source = "clear-source",
                    artist = "Orphan Artist",
                    album = "Orphan Album",
                    genre = "Orphan Genre",
                ),
            )),
            "clear-source",
        )
        db.mediaDao().insert(
            Snapshot(listOf(sourceAudio(
                id = "clear-current",
                source = "clear-source",
                artist = "Shared Artist",
                album = "Shared Album",
                genre = "Shared Genre",
            ))),
            "clear-source",
        )

        db.mediaDao().clearUnavailableMedia()

        assertNotNull(audioDao.getAudio("clear-current").firstOrNull())
        assertEquals(null, audioDao.getAudio("clear-shared-missing").firstOrNull())
        assertEquals(null, audioDao.getAudio("clear-orphan-missing").firstOrNull())

        val artists = artistDao.getAllArtist().firstOrNull().orEmpty()
        val albums = albumDao.getAllAlbum().firstOrNull().orEmpty()
        val genres = genreDao.getAllGenre().firstOrNull().orEmpty()
        assertTrue(artists.any { it.title == "Shared Artist" })
        assertTrue(albums.any { it.title == "Shared Album" })
        assertTrue(genres.any { it.title == "Shared Genre" })
        assertTrue(artists.none { it.title == "Orphan Artist" })
        assertTrue(albums.none { it.title == "Orphan Album" })
        assertTrue(genres.none { it.title == "Orphan Genre" })

        val current = audioDao.getAudioWithRelations("clear-current").firstOrNull()
        assertNotNull(current)
        assertEquals(listOf("Shared Artist"), current.artists.map { it.title })
        assertEquals(listOf("Shared Album"), current.albums.map { it.title })
        assertEquals(listOf("Shared Genre"), current.genres.map { it.title })
    }

    @Test
    fun clearUnavailableAlsoRepairsPreviouslyDanglingRelations() = runTest {
        val audio = sourceAudio(
            id = "dangling-audio",
            source = "dangling-source",
            artist = "Dangling Artist",
            album = "Dangling Album",
            genre = "Dangling Genre",
        )
        db.mediaDao().insert(Snapshot(listOf(audio)), "dangling-source")

        // 模拟旧清理逻辑只删除歌曲、留下交叉表关系的数据库状态。
        audioDao.delete(audio.copy(available = true).toEntity())
        db.mediaDao().clearUnavailableMedia()

        assertTrue(artistDao.getAllArtist().firstOrNull().orEmpty().none {
            it.title == "Dangling Artist"
        })
        assertTrue(albumDao.getAllAlbum().firstOrNull().orEmpty().none {
            it.title == "Dangling Album"
        })
        assertTrue(genreDao.getAllGenre().firstOrNull().orEmpty().none {
            it.title == "Dangling Genre"
        })
    }

    private fun audioExtra(
        artist: String,
        album: String,
        genre: String,
    ): Map<String, String> = mapOf(
        LAudioExtraKeys.ArtistName to artist,
        LAudioExtraKeys.AlbumName to album,
        LAudioExtraKeys.Genre to genre,
    )

    private fun sourceAudio(
        id: String,
        source: String,
        artist: String = "Artist",
        album: String = "Album",
        genre: String = "Genre",
    ) = LAudio(
        id = id,
        title = id,
        subtitle = artist,
        mediaSourceName = source,
        extra = audioExtra(artist, album, genre),
    )
}
