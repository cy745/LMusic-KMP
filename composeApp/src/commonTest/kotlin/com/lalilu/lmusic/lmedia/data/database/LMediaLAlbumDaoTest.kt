package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.data.database.relation.CrossRefLAudioXAlbum
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LMediaLAlbumDaoTest {
    private val db = requireDatabase<LMusicDatabase>(forceMemory = true)
    private val albumDao = db.albumDao()

    @Test
    fun testInsertAndQueryAlbum() = runTest {
        val album = LAlbumEntity(id = "album-1", title = "依然范特西", subtitle = "2006")
        albumDao.insert(album)

        val result = albumDao.getAlbum("album-1").firstOrNull()
        assertNotNull(result)
        assertEquals("依然范特西", result.title)
        assertTrue(result.title.isNotEmpty())
    }

    @Test
    fun testGetAudiosByAlbum() = runTest {
        val album = LAlbumEntity(id = "album-2", title = "Test Album", subtitle = "")
        val audio = LAudioEntity(id = "audio-album-q", title = "In Album", subtitle = "", mediaSourceName = "test")

        albumDao.insert(album)
        albumDao.insertRelation(listOf(CrossRefLAudioXAlbum(album.id, audio.id)))

        // Need audioDao to insert the audio itself
        db.audioDao().insert(audio)

        val audios = albumDao.getAudiosByAlbum("album-2").firstOrNull()
        assertNotNull(audios)
        assertTrue(audios.any { it.id == "audio-album-q" })
    }
}
