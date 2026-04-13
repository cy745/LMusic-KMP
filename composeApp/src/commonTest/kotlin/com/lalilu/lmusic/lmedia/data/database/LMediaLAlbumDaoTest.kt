package com.lalilu.lmusic.lmedia.data.database

import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.ref
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
    private val db = requireDatabase<LMusicDatabase>(forceMemory = false)
    private val albumDao = db.albumDao()
    private val audioDao = db.audioDao()

    @Test
    fun testInsertAndRetrieveAlbum() = runTest {
        val album = LAlbum(
            id = "album-1",
            title = "依然范特西",
            subtitle = "2006"
        )

        // 插入
        albumDao.insert(album)

        // 查询所有
        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertTrue(allAlbums.isNotEmpty())
        assertTrue(allAlbums.any { it.id == "album-1" })

        // 按 ID 查询
        val singleAlbum = albumDao.getAlbum("album-1").firstOrNull()
        assertNotNull(singleAlbum)
        assertEquals("依然范特西", singleAlbum.title)
    }

    @Test
    fun testUpdateAlbum() = runTest {
        val album = LAlbum(
            id = "album-2",
            title = "原标题",
            subtitle = "原副标题"
        )

        albumDao.insert(album)

        // 更新
        val updated = album.copy(title = "新标题", subtitle = "新副标题")
        albumDao.update(updated)

        // 验证
        val result = albumDao.getAlbum("album-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteAlbum() = runTest {
        val album = LAlbum(
            id = "album-3",
            title = "待删除",
            subtitle = ""
        )

        albumDao.insert(album)

        // 验证存在
        var result = albumDao.getAlbum("album-3").firstOrNull()
        assertNotNull(result)

        // 删除
        albumDao.delete(album)

        // 验证删除
        result = albumDao.getAlbum("album-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testAlbumWithAudios() = runTest {
        // 创建专辑
        val album = LAlbum(
            id = "album-4",
            title = "七里香",
            subtitle = "2004"
        )

        // 创建关联的音频
        val audio1 = LAudio(
            id = "audio-album-1",
            title = "七里香",
            subtitle = "",
            mediaSourceName = "local"
        )
        val audio2 = LAudio(
            id = "audio-album-2",
            title = "借口",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 插入数据
        audioDao.insert(audio1)
        audioDao.insert(audio2)
        albumDao.insert(album)
        albumDao.insertRelation(
            listOf(
                CrossRefLAudioXAlbum(album.id, audio1.id),
                CrossRefLAudioXAlbum(album.id, audio2.id)
            )
        )

        // 验证关联查询
        val result = albumDao.getAlbum("album-4").firstOrNull()
        assertNotNull(result)

        // 通过 ref 获取关联的音频
        val linkedAudios = result.ref<LAudio>()
        assertEquals(2, linkedAudios.size)
        assertTrue(linkedAudios.any { it.id == "audio-album-1" })
        assertTrue(linkedAudios.any { it.id == "audio-album-2" })
    }

    @Test
    fun testGetAudiosByAlbum() = runTest {
        // 创建专辑
        val album = LAlbum(
            id = "album-5",
            title = "十一月的萧邦",
            subtitle = "2005"
        )

        // 创建关联的音频
        val audio = LAudio(
            id = "audio-by-album-1",
            title = "夜曲",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 插入
        audioDao.insert(audio)
        albumDao.insert(album)
        albumDao.insertRelation(listOf(CrossRefLAudioXAlbum(album.id, audio.id)))

        // 使用 getAudiosByAlbum 查询
        val audios = albumDao.getAudiosByAlbum("album-5").firstOrNull()
        assertNotNull(audios)
        assertEquals(1, audios.size)
        assertEquals("夜曲", audios[0].title)
    }

    @Test
    fun testInsertAllAlbums() = runTest {
        val albums = listOf(
            LAlbum(id = "album-a", title = "专辑A", subtitle = ""),
            LAlbum(id = "album-b", title = "专辑B", subtitle = ""),
            LAlbum(id = "album-c", title = "专辑C", subtitle = "")
        )

        albumDao.insertAll(albums)

        val all = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(all)
        assertEquals(3, all.size)
    }
}
