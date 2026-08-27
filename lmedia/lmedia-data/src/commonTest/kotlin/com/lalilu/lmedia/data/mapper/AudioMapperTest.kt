package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioMapperTest {

    @Test
    fun `toDomain maps regular fields`() {
        val entity = LAudioEntity(
            id = "audio_test_1",
            title = "Test Song",
            subtitle = "Test Artist",
            mediaSourceName = "local",
            extra = mapOf("key" to "value"),
            available = true,
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.subtitle, domain.subtitle)
        assertEquals(entity.mediaSourceName, domain.mediaSourceName)
        assertEquals(entity.extra, domain.extra)
        assertEquals(entity.available, domain.available)
    }

    @Test
    fun `toEntity writes only extra and clears legacy metadata`() {
        val domain = LAudio(
            id = "audio_test_2",
            title = "Another Song",
            subtitle = "Another Artist",
            mediaSourceName = "remote",
            extra = mapOf(LAudioExtraKeys.Duration to "200000"),
            available = false,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.extra, entity.extra)
        assertEquals("{}", entity.legacyMetadataJson)
        assertEquals(domain.available, entity.available)
    }

    @Test
    fun `legacy metadata is migrated into standard extra on read`() {
        val entity = LAudioEntity(
            id = "audio_legacy",
            legacyMetadataJson = """
                {
                  "artist":"Legacy Artist",
                  "album":"Legacy Album",
                  "albumArtist":"Album Artist",
                  "genre":"Pop",
                  "duration":1234,
                  "title":"Ignored Legacy Title"
                }
            """.trimIndent(),
        )

        val extra = entity.toDomain().extra.orEmpty()

        assertEquals("Legacy Artist", extra[LAudioExtraKeys.ArtistName])
        assertEquals("Legacy Album", extra[LAudioExtraKeys.AlbumName])
        assertEquals("Album Artist", extra[LAudioExtraKeys.AlbumArtist])
        assertEquals("Pop", extra[LAudioExtraKeys.Genre])
        assertEquals("1234", extra[LAudioExtraKeys.Duration])
        assertNull(extra["title"])
    }

    @Test
    fun `current extra wins over legacy metadata`() {
        val domain = LAudioEntity(
            legacyMetadataJson = """{"artist":"Legacy","duration":100}""",
            extra = mapOf(
                LAudioExtraKeys.ArtistName to "Current",
                LAudioExtraKeys.Duration to "200",
            ),
        ).toDomain()

        assertEquals("Current", domain.extra?.get(LAudioExtraKeys.ArtistName))
        assertEquals("200", domain.extra?.get(LAudioExtraKeys.Duration))
    }

    @Test
    fun `legacy extra aliases are exposed through standard keys`() {
        val extra = LAudioEntity(
            extra = mapOf(
                "date_added" to "10",
                "date_modified" to "20",
                "year" to "2026",
            ),
        ).toDomain().extra.orEmpty()

        assertEquals("10", extra[LAudioExtraKeys.DateAdded])
        assertEquals("20", extra[LAudioExtraKeys.DateModified])
        assertEquals("2026", extra[LAudioExtraKeys.Date])
        assertEquals("10", extra["date_added"])
    }

    @Test
    fun `invalid or empty legacy metadata produces null extra`() {
        assertNull(LAudioEntity(legacyMetadataJson = "broken").toDomain().extra)
        assertNull(LAudioEntity().toDomain().extra)
    }
}
