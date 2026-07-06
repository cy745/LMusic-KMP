package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXLArtist
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLArtistDaoTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val artistDao = db.artistDao()
    private val audioDao = db.audioDao()

    @Test
    fun testInsertAndRetrieveArtist() = runTest {
        val artist = LArtistEntity(id = "artist-1", title = "周杰伦", subtitle = "Jay Chou")

        artistDao.insert(artist)

        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertTrue(allArtists.isNotEmpty())
        assertTrue(allArtists.any { it.id == "artist-1" })

        val singleArtist = artistDao.getArtist("artist-1").firstOrNull()
        assertNotNull(singleArtist)
        assertEquals("周杰伦", singleArtist.title)
    }

    @Test
    fun testUpdateArtist() = runTest {
        val artist = LArtistEntity(id = "artist-2", title = "原标题", subtitle = "原副标题")
        artistDao.insert(artist)

        val updated = artist.copy(title = "新标题", subtitle = "新副标题")
        artistDao.update(updated)

        val result = artistDao.getArtist("artist-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteArtist() = runTest {
        val artist = LArtistEntity(id = "artist-3", title = "待删除艺术家", subtitle = "")
        artistDao.insert(artist)

        var result = artistDao.getArtist("artist-3").firstOrNull()
        assertNotNull(result)

        artistDao.delete(artist)

        result = artistDao.getArtist("artist-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testGetAudiosByArtist() = runTest {
        val artist = LArtistEntity(id = "artist-4", title = "ArtistX", subtitle = "")
        val audio = LAudioEntity(id = "audio-artist-1", title = "Song1", subtitle = "", mediaSourceName = "local")

        audioDao.insert(audio)
        artistDao.insert(artist)
        artistDao.insertRelation(listOf(CrossRefLAudioXLArtist(artistId = artist.id, songId = audio.id)))

        val audios = artistDao.getAudiosByArtist("artist-4").firstOrNull()
        assertNotNull(audios)
        assertEquals(1, audios.size)
        assertEquals("Song1", audios[0].title)
    }

    @Test
    fun testInsertAllArtists() = runTest {
        val artists = listOf(
            LArtistEntity(id = "artist-a", title = "Artist A", subtitle = ""),
            LArtistEntity(id = "artist-b", title = "Artist B", subtitle = ""),
            LArtistEntity(id = "artist-c", title = "Artist C", subtitle = "")
        )

        artistDao.insertAll(artists)

        val all = artistDao.getAllArtist().firstOrNull()
        assertNotNull(all)
        assertTrue(all.size >= 3)
    }
}
