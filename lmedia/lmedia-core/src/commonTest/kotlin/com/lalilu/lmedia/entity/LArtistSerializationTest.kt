package com.lalilu.lmedia.entity

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LArtistSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `test LArtist serialization with default values`() {
        val artist = LArtist(
            id = "artist-1",
            title = "Test Artist"
        )

        val serialized = json.encodeToString(artist)
        val deserialized = json.decodeFromString<LArtist>(serialized)

        assertEquals(artist.id, deserialized.id)
        assertEquals(artist.title, deserialized.title)
    }

    @Test
    fun `test LArtist serialization with subtitle and extra`() {
        val extra = mapOf(
            "imageUrl" to "https://example.com/artist.jpg",
            "bio" to "Test biography"
        )
        val artist = LArtist(
            id = "artist-2",
            title = "Famous Artist",
            subtitle = "Pop Singer",
            extra = extra
        )

        val serialized = json.encodeToString(artist)
        val deserialized = json.decodeFromString<LArtist>(serialized)

        assertEquals("Famous Artist", deserialized.title)
        assertEquals("Pop Singer", deserialized.subtitle)
        assertEquals(extra["imageUrl"], deserialized.extra["imageUrl"])
    }

    @Test
    fun `test LArtist serialization with items`() {
        val audios = listOf(
            LAudio(id = "song-1", title = "Song 1", mediaSourceName = "local"),
            LAudio(id = "song-2", title = "Song 2", mediaSourceName = "local")
        )
        val artist = LArtist(
            id = "artist-3",
            title = "Artist with Songs",
            items = audios
        )

        val serialized = json.encodeToString(artist)
        val deserialized = json.decodeFromString<LArtist>(serialized)

        assertEquals(2, deserialized.items.size)
        assertEquals("Song 1", deserialized.items[0].title)
        assertEquals("Song 2", deserialized.items[1].title)
    }

    @Test
    fun `test LArtist list serialization`() {
        val artists = listOf(
            LArtist(id = "1", title = "Artist A"),
            LArtist(id = "2", title = "Artist B"),
            LArtist(id = "3", title = "Artist C")
        )

        val serialized = json.encodeToString(artists)
        val deserialized = json.decodeFromString<List<LArtist>>(serialized)

        assertEquals(3, deserialized.size)
        assertEquals("Artist A", deserialized[0].title)
        assertEquals("Artist B", deserialized[1].title)
        assertEquals("Artist C", deserialized[2].title)
    }

    @Test
    fun `test LArtist copy works correctly`() {
        val artist = LArtist(
            id = "original",
            title = "Original Title",
            subtitle = "Original Subtitle"
        )

        val copied = artist.copy(
            id = "copied",
            title = "Copied Title"
        )

        assertEquals("original", artist.id)
        assertEquals("Original Title", artist.title)
        assertEquals("copied", copied.id)
        assertEquals("Copied Title", copied.title)
        assertEquals("Original Subtitle", copied.subtitle)
    }

    @Test
    fun `test LArtist equals works correctly`() {
        val artist1 = LArtist(id = "test", title = "Test Artist")
        val artist2 = LArtist(id = "test", title = "Test Artist")
        val artist3 = LArtist(id = "test", title = "Different Artist")

        assertEquals(artist1, artist2)
        assertEquals(artist1.hashCode(), artist2.hashCode())
        assertEquals(artist1.id, artist3.id)
    }

    @Test
    fun `test separate extension function`() {
        val artist = LArtist(
            id = "combined",
            title = "Artist A/Artist B;Artist C,Artist D",
            subtitle = "Group"
        )

        val separated = listOf(artist).separate()

        assertEquals(4, separated.size)
        assertEquals("Artist A", separated[0].title)
        assertEquals("Artist B", separated[1].title)
        assertEquals("Artist C", separated[2].title)
        assertEquals("Artist D", separated[3].title)
    }

    @Test
    fun `test merge extension function`() {
        val artists = listOf(
            LArtist(id = "same", title = "Artist X", items = listOf(LAudio(id = "1", title = "Song 1", mediaSourceName = "local"))),
            LArtist(id = "same", title = "Artist X", items = listOf(LAudio(id = "2", title = "Song 2", mediaSourceName = "local"))),
            LArtist(id = "different", title = "Artist Y", items = emptyList())
        )

        val merged = artists.merge()

        assertEquals(2, merged.size)
        val artistX = merged.find { it.title == "Artist X" }
        assertEquals(2, artistX?.items?.size)
    }
}
