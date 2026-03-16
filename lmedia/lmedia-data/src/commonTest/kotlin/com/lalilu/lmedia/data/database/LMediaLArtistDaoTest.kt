package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLArtistDaoTest {
    private val db = requireDatabase<LMediaDatabase>(forceMemory = false)
    private val artistDao = db.artistDao()
    private val audioDao = db.audioDao()

    @Test
    fun testInsertAndRetrieveArtist() = runTest {
        val artist = LArtist(
            id = "artist-1",
            title = "周杰伦",
            subtitle = "Jay Chou"
        )

        // 插入
        artistDao.insert(artist)

        // 查询所有
        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertTrue(allArtists.isNotEmpty())
        assertTrue(allArtists.any { it.id == "artist-1" })

        // 按 ID 查询
        val singleArtist = artistDao.getArtist("artist-1").firstOrNull()
        assertNotNull(singleArtist)
        assertEquals("周杰伦", singleArtist.title)
    }

    @Test
    fun testUpdateArtist() = runTest {
        val artist = LArtist(
            id = "artist-2",
            title = "原标题",
            subtitle = "原副标题"
        )

        artistDao.insert(artist)

        // 更新
        val updated = artist.copy(title = "新标题", subtitle = "新副标题")
        artistDao.update(updated)

        // 验证
        val result = artistDao.getArtist("artist-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteArtist() = runTest {
        val artist = LArtist(
            id = "artist-3",
            title = "待删除艺术家",
            subtitle = ""
        )

        artistDao.insert(artist)

        // 验证存在
        var result = artistDao.getArtist("artist-3").firstOrNull()
        assertNotNull(result)

        // 删除
        artistDao.delete(artist)

        // 验证删除
        result = artistDao.getArtist("artist-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testGetAudiosByArtist() = runTest {
        // 创建艺术家
        val artist = LArtist(
            id = "artist-4",
            title = "ArtistX",
            subtitle = ""
        )

        // 创建关联的音频
        val audio = LAudio(
            id = "audio-artist-1",
            title = "Song1",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 手动建立关联并分别插入
        audioDao.insert(audio)
        artistDao.insert(artist)
        artistDao.insertRelation(listOf(
            com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist(
                artistId = artist.id,
                songId = audio.id
            )
        ))

        // 使用 getAudiosByArtist 查询
        val audios = artistDao.getAudiosByArtist("artist-4").firstOrNull()
        assertNotNull(audios)
        assertEquals(1, audios.size)
        assertEquals("Song1", audios[0].title)
    }

    @Test
    fun testInsertAllArtists() = runTest {
        val artists = listOf(
            LArtist(id = "artist-a", title = "Artist A", subtitle = ""),
            LArtist(id = "artist-b", title = "Artist B", subtitle = ""),
            LArtist(id = "artist-c", title = "Artist C", subtitle = "")
        )

        artistDao.insertAll(artists)

        val all = artistDao.getAllArtist().firstOrNull()
        assertNotNull(all)
        assertEquals(3, all.size)
    }
}
