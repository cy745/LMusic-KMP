package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LGenre
import com.lalilu.lmedia.entity.link
import com.lalilu.lmedia.entity.ref
import com.lalilu.lmedia.entity.relation.CrossRefLAudioXGenre
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLGenreDaoTest {
    private val db = requireDatabase<LMediaDatabase>(forceMemory = false)
    private val genreDao = db.genreDao()
    private val audioDao = db.audioDao()

    @Test
    fun testInsertAndRetrieveGenre() = runTest {
        val genre = LGenre(
            id = "genre-1",
            title = "流行",
            subtitle = "Pop"
        )

        // 插入
        genreDao.insert(genre)

        // 查询所有
        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertTrue(allGenres.isNotEmpty())
        assertTrue(allGenres.any { it.id == "genre-1" })

        // 按 ID 查询
        val singleGenre = genreDao.getGenre("genre-1").firstOrNull()
        assertNotNull(singleGenre)
        assertEquals("流行", singleGenre.title)
    }

    @Test
    fun testUpdateGenre() = runTest {
        val genre = LGenre(
            id = "genre-2",
            title = "原标题",
            subtitle = "原副标题"
        )

        genreDao.insert(genre)

        // 更新
        val updated = genre.copy(title = "新标题", subtitle = "新副标题")
        genreDao.update(updated)

        // 验证
        val result = genreDao.getGenre("genre-2").firstOrNull()
        assertNotNull(result)
        assertEquals("新标题", result.title)
        assertEquals("新副标题", result.subtitle)
    }

    @Test
    fun testDeleteGenre() = runTest {
        val genre = LGenre(
            id = "genre-3",
            title = "待删除",
            subtitle = ""
        )

        genreDao.insert(genre)

        // 验证存在
        var result = genreDao.getGenre("genre-3").firstOrNull()
        assertNotNull(result)

        // 删除
        genreDao.delete(genre)

        // 验证删除
        result = genreDao.getGenre("genre-3").firstOrNull()
        assertTrue(result == null)
    }

    @Test
    fun testGenreWithAudios() = runTest {
        // 创建流派
        val genre = LGenre(
            id = "genre-4",
            title = "摇滚",
            subtitle = "Rock"
        )

        // 创建关联的音频
        val audio1 = LAudio(
            id = "audio-genre-1",
            title = "摇滚歌曲1",
            subtitle = "",
            mediaSourceName = "local"
        )
        val audio2 = LAudio(
            id = "audio-genre-2",
            title = "摇滚歌曲2",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 插入数据
        audioDao.insert(audio1)
        audioDao.insert(audio2)
        genreDao.insert(genre)
        genreDao.insertRelation(listOf(
            CrossRefLAudioXGenre(genre.id, audio1.id),
            CrossRefLAudioXGenre(genre.id, audio2.id)
        ))

        // 验证关联查询
        val result = genreDao.getGenre("genre-4").firstOrNull()
        assertNotNull(result)

        // 通过 ref 获取关联的音频
        val linkedAudios = result.ref<LAudio>()
        assertEquals(2, linkedAudios.size)
        assertTrue(linkedAudios.any { it.id == "audio-genre-1" })
        assertTrue(linkedAudios.any { it.id == "audio-genre-2" })
    }

    @Test
    fun testGetAudiosByGenre() = runTest {
        // 创建流派
        val genre = LGenre(
            id = "genre-5",
            title = "爵士",
            subtitle = "Jazz"
        )

        // 创建关联的音频
        val audio = LAudio(
            id = "audio-by-genre-1",
            title = "爵士乐",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 插入
        audioDao.insert(audio)
        genreDao.insert(genre)
        genreDao.insertRelation(listOf(CrossRefLAudioXGenre(genre.id, audio.id)))

        // 使用 getAudiosByGenre 查询
        val audios = genreDao.getAudiosByGenre("genre-5").firstOrNull()
        assertNotNull(audios)
        assertEquals(1, audios.size)
        assertEquals("爵士乐", audios[0].title)
    }

    @Test
    fun testInsertAllGenres() = runTest {
        val genres = listOf(
            LGenre(id = "genre-a", title = "流行", subtitle = ""),
            LGenre(id = "genre-b", title = "摇滚", subtitle = ""),
            LGenre(id = "genre-c", title = "古典", subtitle = "")
        )

        genreDao.insertAll(genres)

        val all = genreDao.getAllGenre().firstOrNull()
        assertNotNull(all)
        assertEquals(3, all.size)
    }

    @Test
    fun testMultipleGenresFromDifferentAudios() = runTest {
        // 创建不同流派的音频
        val audio1 = LAudio(
            id = "audio-diff-genre-1",
            title = "流行歌曲",
            subtitle = "",
            mediaSourceName = "local"
        )
        val audio2 = LAudio(
            id = "audio-diff-genre-2",
            title = "摇滚歌曲",
            subtitle = "",
            mediaSourceName = "local"
        )

        // 创建流派
        val popGenre = LGenre(
            id = "genre-pop",
            title = "流行",
            subtitle = ""
        )
        val rockGenre = LGenre(
            id = "genre-rock",
            title = "摇滚",
            subtitle = ""
        )

        // 插入
        audioDao.insert(audio1)
        audioDao.insert(audio2)
        genreDao.insert(popGenre)
        genreDao.insert(rockGenre)

        // 手动插入关联关系
        genreDao.insertRelation(listOf(
            CrossRefLAudioXGenre(popGenre.id, audio1.id),
            CrossRefLAudioXGenre(rockGenre.id, audio2.id)
        ))

        // 验证流行流派
        val popResult = genreDao.getGenre("genre-pop").firstOrNull()
        assertNotNull(popResult)
        val popSongs = popResult.ref<LAudio>()
        assertEquals(1, popSongs.size)
        assertEquals("流行歌曲", popSongs[0].title)

        // 验证摇滚流派
        val rockResult = genreDao.getGenre("genre-rock").firstOrNull()
        assertNotNull(rockResult)
        val rockSongs = rockResult.ref<LAudio>()
        assertEquals(1, rockSongs.size)
        assertEquals("摇滚歌曲", rockSongs[0].title)
    }
}
