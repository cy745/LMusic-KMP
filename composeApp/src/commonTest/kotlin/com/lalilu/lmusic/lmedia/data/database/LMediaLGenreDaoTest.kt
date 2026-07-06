package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXGenre
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLGenreDaoTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val genreDao = db.genreDao()
    private val audioDao = db.audioDao()

    @Test
    fun testInsertAndRetrieveGenre() = runTest {
        val genre = LGenreEntity(id = "genre-1", title = "流行", subtitle = "Pop")
        genreDao.insert(genre)

        val all = genreDao.getAllGenre().firstOrNull()
        assertNotNull(all)
        assertTrue(all.any { it.id == "genre-1" })

        val single = genreDao.getGenre("genre-1").firstOrNull()
        assertNotNull(single)
        assertEquals("流行", single.title)
    }

    @Test
    fun testGetAudiosByGenre() = runTest {
        val genre = LGenreEntity(id = "genre-2", title = "Rock", subtitle = "")
        val audio = LAudioEntity(id = "audio-genre-q", title = "Rock Song", subtitle = "", mediaSourceName = "test")

        genreDao.insert(genre)
        audioDao.insert(audio)
        genreDao.insertRelation(listOf(CrossRefLAudioXGenre(genreId = genre.id, songId = audio.id)))

        val audios = genreDao.getAudiosByGenre("genre-2").firstOrNull()
        assertNotNull(audios)
        assertEquals(1, audios.size)
        assertEquals("Rock Song", audios[0].title)
    }
}
