package com.lalilu.lmedia.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LAudioExtraTest {
    @Test
    fun readsStandardFieldsAndSplitsArtistNames() {
        val audio = LAudio(
            subtitle = "subtitle artist",
            extra = mapOf(
                LAudioExtraKeys.ArtistName to "artist A/artist B",
                LAudioExtraKeys.AlbumName to "new album",
                LAudioExtraKeys.Duration to "2000",
            ),
        )

        assertEquals("artist A/artist B", audio.artistName)
        assertEquals(listOf("artist A", "artist B"), audio.artistNames())
        assertEquals("new album", audio.albumName)
        assertEquals(2000L, audio.duration)
    }

    @Test
    fun subtitleIsArtistFallbackWhenStandardFieldIsMissing() {
        val audio = LAudio(subtitle = "subtitle artist")

        assertEquals("subtitle artist", audio.artistName)
        assertEquals(0L, audio.duration)
    }
}
