package com.lalilu.lmedia.entity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LAudioSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `test LAudio serialization with default values`() {
        val audio = LAudio(
            id = "test-id",
            title = "Test Title",
            subtitle = "Test Subtitle",
            mediaSourceName = "local"
        )

        val serialized = json.encodeToString(audio)
        val deserialized = json.decodeFromString<LAudio>(serialized)

        assertEquals(audio.id, deserialized.id)
        assertEquals(audio.title, deserialized.title)
        assertEquals(audio.subtitle, deserialized.subtitle)
        assertEquals(audio.mediaSourceName, deserialized.mediaSourceName)
    }

    @Test
    fun `test LAudio serialization with metadata`() {
        val audio = LAudio(
            id = "test-id-2",
            title = "Test Song",
            subtitle = "Artist Name",
            mediaSourceName = "media-store",
            metadata = Metadata(
                title = "Song Title",
                album = "Album Name",
                artist = "Artist Name",
                albumArtist = "Album Artist",
                composer = "Composer",
                lyricist = "Lyricist",
                comment = "A great song",
                genre = "Rock",
                track = "1",
                disc = "1",
                date = "2024-01-01",
                duration = 180000L,
                dateAdded = 1704067200000L,
                dateModified = 1704153600000L
            )
        )

        val serialized = json.encodeToString(audio)
        val deserialized = json.decodeFromString<LAudio>(serialized)

        assertEquals(audio.id, deserialized.id)
        assertEquals(audio.title, deserialized.title)
        assertEquals(audio.metadata.album, deserialized.metadata.album)
        assertEquals(audio.metadata.artist, deserialized.metadata.artist)
        assertEquals(audio.metadata.duration, deserialized.metadata.duration)
    }

    @Test
    fun `test LAudio serialization with extra map`() {
        val extra = mapOf(
            "coverUrl" to "https://example.com/cover.jpg",
            "lyricsUrl" to "https://example.com/lyrics.lrc"
        )
        val audio = LAudio(
            id = "test-id-3",
            title = "Test with Extra",
            subtitle = "Artist",
            extra = extra,
            mediaSourceName = "remote"
        )

        val serialized = json.encodeToString(audio)
        val deserialized = json.decodeFromString<LAudio>(serialized)

        assertEquals(extra["coverUrl"], deserialized.extra["coverUrl"])
        assertEquals(extra["lyricsUrl"], deserialized.extra["lyricsUrl"])
    }

    @Test
    fun `test Metadata serialization`() {
        val metadata = Metadata(
            title = "Test Title",
            album = "Test Album",
            artist = "Test Artist",
            albumArtist = "Test Album Artist",
            composer = "Test Composer",
            lyricist = "Test Lyricist",
            comment = "Test Comment",
            genre = "Pop",
            track = "5",
            disc = "1",
            date = "2023-12-25",
            duration = 240000L,
            dateAdded = 1703452800000L,
            dateModified = 1703539200000L
        )

        val serialized = json.encodeToString(metadata)
        val deserialized = json.decodeFromString<Metadata>(serialized)

        assertEquals(metadata.title, deserialized.title)
        assertEquals(metadata.album, deserialized.album)
        assertEquals(metadata.artist, deserialized.artist)
        assertEquals(metadata.duration, deserialized.duration)
    }

    @Test
    fun `test Metadata empty serialization`() {
        val metadata = Metadata.EMPTY

        val serialized = json.encodeToString(metadata)
        val deserialized = json.decodeFromString<Metadata>(serialized)

        assertEquals("", deserialized.title)
        assertEquals("", deserialized.album)
        assertEquals(0L, deserialized.duration)
    }

    @Test
    fun `test LAudio list serialization`() {
        val audios = listOf(
            LAudio(id = "1", title = "Song 1", mediaSourceName = "local"),
            LAudio(id = "2", title = "Song 2", mediaSourceName = "local"),
            LAudio(id = "3", title = "Song 3", mediaSourceName = "remote")
        )

        val serialized = json.encodeToString(audios)
        val deserialized = json.decodeFromString<List<LAudio>>(serialized)

        assertEquals(3, deserialized.size)
        assertEquals("Song 1", deserialized[0].title)
        assertEquals("Song 2", deserialized[1].title)
        assertEquals("Song 3", deserialized[2].title)
    }
}
