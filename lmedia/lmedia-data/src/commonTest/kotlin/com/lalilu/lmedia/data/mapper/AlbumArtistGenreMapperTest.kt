package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAlbumEntity
import com.lalilu.lmedia.data.entity.LArtistEntity
import com.lalilu.lmedia.data.entity.LGenreEntity
import com.lalilu.lmedia.data.entity.LFolderEntity
import com.lalilu.lmedia.domain.model.LAlbum
import com.lalilu.lmedia.domain.model.LArtist
import com.lalilu.lmedia.domain.model.LFolder
import com.lalilu.lmedia.domain.model.LGenre
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumArtistGenreMapperTest {

    @Test
    fun `AlbumEntity roundtrip`() {
        val entity = LAlbumEntity(id = "album_test", title = "Test Album", subtitle = "Sub", extra = mapOf("year" to "2024"))
        val roundtripped = entity.toDomain().toEntity()
        assertEquals(entity, roundtripped)
    }

    @Test
    fun `ArtistEntity roundtrip`() {
        val entity = LArtistEntity(id = "artist_test", title = "Test Artist", subtitle = "Sub", extra = null)
        val roundtripped = entity.toDomain().toEntity()
        assertEquals(entity, roundtripped)
    }

    @Test
    fun `GenreEntity roundtrip`() {
        val entity = LGenreEntity(id = "genre_test", title = "Rock", subtitle = "", extra = null)
        val roundtripped = entity.toDomain().toEntity()
        assertEquals(entity, roundtripped)
    }

    @Test
    fun `FolderEntity roundtrip`() {
        val entity = LFolderEntity(id = "folder_test", title = "My Folder", subtitle = "/path/to/folder", extra = null)
        val roundtripped = entity.toDomain().toEntity()
        assertEquals(entity, roundtripped)
    }

    @Test
    fun `Album toDomain maps correctly`() {
        val entity = LAlbumEntity(id = "album_1", title = "Album", subtitle = "Desc")
        val domain = entity.toDomain()
        assertEquals("album_1", domain.id)
        assertEquals("Album", domain.title)
    }

    @Test
    fun `Artist toDomain maps correctly`() {
        val entity = LArtistEntity(id = "artist_1", title = "Artist", subtitle = "Info")
        val domain = entity.toDomain()
        assertEquals("artist_1", domain.id)
        assertEquals("Artist", domain.title)
    }
}
