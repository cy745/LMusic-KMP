package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LArtist
import com.lalilu.lmedia.entity.LAlbum
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXLArtist
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXAlbum
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXGenre
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLAudioDaoTest {
    private val db = requireDatabase<LMediaDatabase>(forceMemory = false)
    private val audioDao = db.audioDao()
    private val artistDao = db.artistDao()
    private val albumDao = db.albumDao()
    private val genreDao = db.genreDao()

    @Test
    fun testInsertAndRetrieveAudio() = runTest {
        val audio = LAudio(
            id = "audio-1",
            title = "夜的第七章",
            subtitle = "专辑: 依然范特西",
            mediaSourceName = "local"
        )

        // 插入
        audioDao.insert(audio)

        // 查询所有
        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertTrue(allAudios.isNotEmpty())
        assertTrue(allAudios.any { it.id == "audio-1" })

        // 按 ID 查询
        val singleAudio = audioDao.getAudio("audio-1").firstOrNull()
        assertNotNull(singleAudio)
        assertEquals("夜的第七章", singleAudio.title)
    }

    @Test
    fun testUpdateAudio() = runTest {
        val audio = LAudio(
            id = "audio-2",
            title = "原标题",
            subtitle = "原副标题",
            mediaSourceName = "local"
        )

        audioDao.insert(audio)

        // 更新
        val updated = audio.copy(title = "新标题", subtitle = "新副标题")
        audioDao.update(updated)

        // 验证
        val result = audioDao.getAudio("audio-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteAudio() = runTest {
        val audio = LAudio(
            id = "audio-3",
            title = "待删除",
            subtitle = "",
            mediaSourceName = "local"
        )

        audioDao.insert(audio)

        // 验证存在
        var result = audioDao.getAudio("audio-3").firstOrNull()
        assertNotNull(result)

        // 删除
        audioDao.delete(audio)

        // 验证删除
        result = audioDao.getAudio("audio-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testAudioWithArtists() = runTest {
        // 创建音频
        val audio = LAudio(
            id = "audio-artist-1",
            title = "告白气球",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 创建艺术家
        val artist = LArtist(
            id = "artist-1",
            title = "周杰伦",
            subtitle = "Jay Chou"
        )

        // 插入数据
        audioDao.insert(audio)
        artistDao.insert(artist)
        artistDao.insertRelation(listOf(CrossRefLAudioXLArtist(artist.id, audio.id)))

        // 验证关联查询
        val result = audioDao.getAudio("audio-artist-1").firstOrNull()
        assertNotNull(result)

        // 通过 ref 获取关联的艺术家
        val linkedArtists = result.ref<LArtist>()
        assertEquals(1, linkedArtists.size)
        assertEquals("artist-1", linkedArtists[0].id)
    }

    @Test
    fun testAudioWithAlbums() = runTest {
        // 创建音频
        val audio = LAudio(
            id = "audio-album-1",
            title = "稻香",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 创建专辑
        val album = LAlbum(
            id = "album-1",
            title = "依然范特西",
            subtitle = "2006"
        )

        // 插入数据
        audioDao.insert(audio)
        albumDao.insert(album)
        albumDao.insertRelation(listOf(CrossRefLAudioXAlbum(album.id, audio.id)))

        // 验证关联查询
        val result = audioDao.getAudio("audio-album-1").firstOrNull()
        assertNotNull(result)

        // 通过 ref 获取关联的专辑
        val linkedAlbums = result.ref<LAlbum>()
        assertEquals(1, linkedAlbums.size)
        assertEquals("album-1", linkedAlbums[0].id)
    }

    @Test
    fun testAudioWithGenres() = runTest {
        // 创建音频
        val audio = LAudio(
            id = "audio-genre-1",
            title = "听妈妈的话",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 创建流派
        val genre = LGenre(
            id = "genre-1",
            title = "流行",
            subtitle = "Pop"
        )

        // 插入数据
        audioDao.insert(audio)
        genreDao.insert(genre)
        genreDao.insertRelation(listOf(CrossRefLAudioXGenre(genre.id, audio.id)))

        // 验证关联查询
        val result = audioDao.getAudio("audio-genre-1").firstOrNull()
        assertNotNull(result)

        // 通过 ref 获取关联的流派
        val linkedGenres = result.ref<LGenre>()
        assertEquals(1, linkedGenres.size)
        assertEquals("genre-1", linkedGenres[0].id)
    }

    @Test
    fun testGetAllAudiosWithRelations() = runTest {
        // 创建音频
        val audio1 = LAudio(
            id = "audio-rel-1",
            title = "歌曲1",
            subtitle = "",
            mediaSourceName = "local"
        )
        val audio2 = LAudio(
            id = "audio-rel-2",
            title = "歌曲2",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 创建关联的艺术家
        val artist = LArtist(
            id = "artist-rel-1",
            title = "艺术家A",
            subtitle = ""
        )

        // 插入
        audioDao.insert(audio1)
        audioDao.insert(audio2)
        artistDao.insert(artist)
        artistDao.insertRelation(listOf(
            CrossRefLAudioXLArtist(artist.id, audio1.id),
            CrossRefLAudioXLArtist(artist.id, audio2.id)
        ))

        // 验证获取所有音频时关联关系正确
        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertEquals(2, allAudios.size)

        // 验证每首歌曲都能通过 ref 获取到关联艺术家
        for (audio in allAudios) {
            val linkedArtists = audio.ref<LArtist>()
            assertTrue(linkedArtists.isNotEmpty())
            assertEquals("artist-rel-1", linkedArtists[0].id)
        }
    }
}
