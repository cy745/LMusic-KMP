package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLAudioDaoTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val audioDao = db.audioDao()
    private val artistDao = db.artistDao()
    private val albumDao = db.albumDao()
    private val genreDao = db.genreDao()

    @Test
    fun testInsertAndRetrieveAudio() = runTest {
        val audio = LAudioEntity(
            id = "audio-1",
            title = "夜的第七章",
            subtitle = "专辑: 依然范特西",
            mediaSourceName = "local"
        )

        audioDao.insert(audio)

        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertTrue(allAudios.isNotEmpty())
        assertTrue(allAudios.any { it.id == "audio-1" })

        val singleAudio = audioDao.getAudio("audio-1").firstOrNull()
        assertNotNull(singleAudio)
        assertEquals("夜的第七章", singleAudio.title)
    }

    @Test
    fun testUpdateAudio() = runTest {
        val audio = LAudioEntity(
            id = "audio-2",
            title = "原标题",
            subtitle = "原副标题",
            mediaSourceName = "local"
        )

        audioDao.insert(audio)

        val updated = audio.copy(title = "新标题", subtitle = "新副标题")
        audioDao.update(updated)

        val result = audioDao.getAudio("audio-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteAudio() = runTest {
        val audio = LAudioEntity(
            id = "audio-3",
            title = "待删除",
            subtitle = "",
            mediaSourceName = "local"
        )

        audioDao.insert(audio)

        var result = audioDao.getAudio("audio-3").firstOrNull()
        assertNotNull(result)

        audioDao.delete(audio)

        result = audioDao.getAudio("audio-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testAudioWithArtists() = runTest {
        val audio = LAudioEntity(
            id = "audio-artist-1",
            title = "告白气球",
            subtitle = "",
            mediaSourceName = "local"
        )

        val artist = LArtistEntity(
            id = "artist-1",
            title = "周杰伦",
            subtitle = "Jay Chou"
        )

        audioDao.insert(audio)
        artistDao.insert(artist)
        artistDao.insertRelation(listOf(CrossRefLAudioXLArtist(artist.id, audio.id)))

        // Verify via the relation query result
        val result = audioDao.getAudioWithRelations("audio-artist-1").firstOrNull()
        assertNotNull(result)
        assertEquals(1, result.artists.size)
        assertEquals("artist-1", result.artists[0].id)
    }

    @Test
    fun testAudioWithAlbums() = runTest {
        val audio = LAudioEntity(
            id = "audio-album-1",
            title = "稻香",
            subtitle = "",
            mediaSourceName = "local"
        )

        val album = LAlbumEntity(
            id = "album-1",
            title = "依然范特西",
            subtitle = "2006"
        )

        audioDao.insert(audio)
        albumDao.insert(album)
        albumDao.insertRelation(listOf(CrossRefLAudioXAlbum(album.id, audio.id)))

        val result = audioDao.getAudioWithRelations("audio-album-1").firstOrNull()
        assertNotNull(result)
        assertEquals(1, result.albums.size)
        assertEquals("album-1", result.albums[0].id)
    }

    @Test
    fun testAudioWithGenres() = runTest {
        val audio = LAudioEntity(
            id = "audio-genre-1",
            title = "听妈妈的话",
            subtitle = "",
            mediaSourceName = "local"
        )

        val genre = LGenreEntity(
            id = "genre-1",
            title = "流行",
            subtitle = "Pop"
        )

        audioDao.insert(audio)
        genreDao.insert(genre)
        genreDao.insertRelation(listOf(CrossRefLAudioXGenre(genre.id, audio.id)))

        val result = audioDao.getAudioWithRelations("audio-genre-1").firstOrNull()
        assertNotNull(result)
        assertEquals(1, result.genres.size)
        assertEquals("genre-1", result.genres[0].id)
    }

    @Test
    fun testGetAllAudiosWithRelations() = runTest {
        val audio1 = LAudioEntity(
            id = "audio-rel-1",
            title = "歌曲1",
            subtitle = "",
            mediaSourceName = "local"
        )
        val audio2 = LAudioEntity(
            id = "audio-rel-2",
            title = "歌曲2",
            subtitle = "",
            mediaSourceName = "local"
        )

        val artist = LArtistEntity(
            id = "artist-rel-1",
            title = "艺术家A",
            subtitle = ""
        )

        audioDao.insert(audio1)
        audioDao.insert(audio2)
        artistDao.insert(artist)
        artistDao.insertRelation(
            listOf(
                CrossRefLAudioXLArtist(artist.id, audio1.id),
                CrossRefLAudioXLArtist(artist.id, audio2.id)
            )
        )

        val allResults = audioDao.getAllAudioWithRelations().firstOrNull()
        assertNotNull(allResults)
        assertEquals(2, allResults.size)

        for (result in allResults) {
            assertTrue(result.artists.isNotEmpty())
            assertEquals("artist-rel-1", result.artists[0].id)
        }
    }
}
