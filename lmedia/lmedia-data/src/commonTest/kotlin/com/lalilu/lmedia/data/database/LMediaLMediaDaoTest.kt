package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Metadata
import com.lalilu.lmedia.entity.buildSnapshot
import com.lalilu.lmedia.entity.ref
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class LMediaLMediaDaoTest {
    private val db = requireDatabase<LMediaDatabase>(forceMemory = false)
    private val audioDao = db.audioDao()
    private val artistDao = db.artistDao()
    private val albumDao = db.albumDao()
    private val genreDao = db.genreDao()

    @Test
    fun testInsertSnapshot() = runTest {
        // 使用 buildSnapshot 从音频列表创建快照
        val snapshot = listOf(
            LAudio(
                id = "song-1",
                title = "夜的第七章",
                subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "依然范特西", genre = "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-2",
                title = "告白气球",
                subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "周杰伦的床边故事", genre = "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-3",
                title = "稻香",
                subtitle = "",
                metadata = Metadata(artist = "周杰伦", album = "依然范特西", genre = "流行"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        // 验证快照内容
        assertEquals(3, snapshot.audios.size)
        assertEquals(2, snapshot.albums.size)  // 依然范特西
        assertEquals(1, snapshot.artists.size) // 周杰伦
        assertEquals(1, snapshot.genres.size)  // 流行

        // 插入快照到数据库
        db.mediaDao().insert(snapshot)

        // 验证音频插入成功
        val allAudios = audioDao.getAllAudio().firstOrNull()
        assertNotNull(allAudios)
        assertEquals(3, allAudios.size)

        // 验证艺术家插入成功
        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertEquals(1, allArtists.size)
        assertEquals("周杰伦", allArtists[0].title)

        // 验证专辑插入成功
        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertEquals(2, allAlbums.size)
        assertEquals("依然范特西", allAlbums[0].title)

        // 验证流派插入成功
        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertEquals(1, allGenres.size)
        assertEquals("流行", allGenres[0].title)
    }

    @Test
    fun testMultipleArtistsFromAudio() = runTest {
        // 测试一首歌曲有多个艺术家的情况
        val snapshot = listOf(
            LAudio(
                id = "song-multi",
                title = "夜曲",
                subtitle = "",
                metadata = Metadata(artist = "周杰伦/方文山", album = "十一月的萧邦", genre = "流行"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        // 验证多个艺术家被正确解析
        assertEquals(2, snapshot.artists.size)

        // 插入数据库
        db.mediaDao().insert(snapshot)

        // 验证两个艺术家都被插入
        val allArtists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(allArtists)
        assertEquals(2, allArtists.size)
    }

    @Test
    fun testMultipleAlbums() = runTest {
        // 测试多张专辑
        val snapshot = listOf(
            LAudio(
                id = "song-a",
                title = "歌曲A",
                subtitle = "",
                metadata = Metadata(artist = "artist-a", album = "专辑A", genre = "流行"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-b",
                title = "歌曲B",
                subtitle = "",
                metadata = Metadata(artist = "artist-b", album = "专辑B", genre = "摇滚"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        assertEquals(2, snapshot.albums.size)
        assertEquals(2, snapshot.artists.size)
        assertEquals(2, snapshot.genres.size)

        db.mediaDao().insert(snapshot)

        val allAlbums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(allAlbums)
        assertEquals(2, allAlbums.size)

        val allGenres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(allGenres)
        assertEquals(2, allGenres.size)
    }

    @Test
    fun testAudioRelationsThroughMediaDao() = runTest {
        // 通过 mediaDao 插入后，验证关联关系
        val snapshot = listOf(
            LAudio(
                id = "song-rel-1",
                title = "关联歌曲1",
                subtitle = "",
                metadata = Metadata(artist = "ArtistX", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-rel-2",
                title = "关联歌曲2",
                subtitle = "",
                metadata = Metadata(artist = "ArtistX", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        db.mediaDao().insert(snapshot)

        // 获取专辑并验证关联
        val albums = albumDao.getAllAlbum().firstOrNull()
        assertNotNull(albums)
        assertEquals(1, albums.size)

        // 验证专辑关联的歌曲
        val album = albums[0]
        val linkedSongs = album.ref<LAudio>()
        assertEquals(2, linkedSongs.size)
        assertTrue(linkedSongs.any { it.id == "song-rel-1" })
        assertTrue(linkedSongs.any { it.id == "song-rel-2" })

        // 获取艺术家并验证关联
        val artists = artistDao.getAllArtist().firstOrNull()
        assertNotNull(artists)
        assertEquals(1, artists.size)

        val artist = artists[0]
        val artistSongs = artist.ref<LAudio>()
        assertEquals(2, artistSongs.size)
    }

    @Test
    fun testGenreRelations() = runTest {
        val snapshot = listOf(
            LAudio(
                id = "song-genre-1",
                title = "歌曲1",
                subtitle = "",
                metadata = Metadata(artist = "A", album = "Album1", genre = "Rock"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-genre-2",
                title = "歌曲2",
                subtitle = "",
                metadata = Metadata(artist = "B", album = "Album2", genre = "Jazz"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-genre-3",
                title = "歌曲3",
                subtitle = "",
                metadata = Metadata(artist = "C", album = "Album3", genre = "Rock"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        assertEquals(2, snapshot.genres.size) // Rock, Jazz

        db.mediaDao().insert(snapshot)

        // 获取 Rock 流派
        val genres = genreDao.getAllGenre().firstOrNull()
        assertNotNull(genres)

        val rockGenre = genres.find { it.title == "Rock" }
        assertNotNull(rockGenre)

        val rockSongs = rockGenre.ref<LAudio>()
        assertEquals(2, rockSongs.size)
    }

    @Test
    fun testGetAudiosByArtist() = runTest {
        val snapshot = listOf(
            LAudio(
                id = "song-artist-1",
                title = "歌曲1",
                subtitle = "",
                metadata = Metadata(artist = "ArtistY", album = "Album1", genre = "Pop"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-artist-2",
                title = "歌曲2",
                subtitle = "",
                metadata = Metadata(artist = "ArtistY", album = "Album2", genre = "Pop"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        db.mediaDao().insert(snapshot)

        // 使用 getAudiosByArtist 查询
        val audios = artistDao.getAudiosByArtist("ArtistY").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByAlbum() = runTest {
        val snapshot = listOf(
            LAudio(
                id = "song-album-1",
                title = "歌曲1",
                subtitle = "",
                metadata = Metadata(artist = "A", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-album-2",
                title = "歌曲2",
                subtitle = "",
                metadata = Metadata(artist = "B", album = "AlbumX", genre = "Pop"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        db.mediaDao().insert(snapshot)

        // 使用 getAudiosByAlbum 查询
        val audios = albumDao.getAudiosByAlbum("AlbumX").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }

    @Test
    fun testGetAudiosByGenre() = runTest {
        val snapshot = listOf(
            LAudio(
                id = "song-g-1",
                title = "歌曲1",
                subtitle = "",
                metadata = Metadata(artist = "A", album = "Album1", genre = "Electronic"),
                mediaSourceName = "local"
            ),
            LAudio(
                id = "song-g-2",
                title = "歌曲2",
                subtitle = "",
                metadata = Metadata(artist = "B", album = "Album2", genre = "Electronic"),
                mediaSourceName = "local"
            )
        ).buildSnapshot()

        db.mediaDao().insert(snapshot)

        // 使用 getAudiosByGenre 查询
        val audios = genreDao.getAudiosByGenre("Electronic").firstOrNull()
        assertNotNull(audios)
        assertEquals(2, audios.size)
    }
}
