package com.lalilu.lmedia.data.database

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaLibraryAssemblerTest {
    @Test
    fun buildsNormalizedEntitiesAndRelationsFromAudioExtra() {
        val batch = MediaLibraryAssembler.assemble(
            listOf(
                audio(
                    id = "one",
                    artist = " Artist A / Artist B ",
                    album = "Album",
                    albumArtist = "Artist A",
                    genre = "Pop",
                ),
                audio(
                    id = "two",
                    artist = "Artist A",
                    album = "Album",
                    albumArtist = "Artist A",
                    genre = "Pop",
                ),
            )
        )

        assertEquals(setOf("artist_Artist A", "artist_Artist B"), batch.artists.map { it.id }.toSet())
        assertEquals(listOf("album_Album|Artist A"), batch.albums.map { it.id })
        assertEquals(listOf("genre_Pop"), batch.genres.map { it.id })
        assertEquals(3, batch.artistRelations.size)
        assertEquals(2, batch.albumRelations.size)
        assertEquals(2, batch.genreRelations.size)
        assertTrue(batch.audios.all { it.available })
    }

    @Test
    fun sameAlbumNameWithDifferentAlbumArtistRemainsSeparate() {
        val batch = MediaLibraryAssembler.assemble(
            listOf(
                audio("one", "A", "Greatest Hits", "A"),
                audio("two", "B", "Greatest Hits", "B"),
            )
        )

        assertEquals(
            setOf("album_Greatest Hits|A", "album_Greatest Hits|B"),
            batch.albums.map { it.id }.toSet(),
        )
    }

    private fun audio(
        id: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String? = null,
    ) = LAudio(
        id = id,
        title = id,
        subtitle = artist,
        mediaSourceName = "source",
        extra = buildMap {
            put(LAudioExtraKeys.ArtistName, artist)
            put(LAudioExtraKeys.AlbumName, album)
            put(LAudioExtraKeys.AlbumArtist, albumArtist)
            genre?.let { put(LAudioExtraKeys.Genre, it) }
        },
    )
}
