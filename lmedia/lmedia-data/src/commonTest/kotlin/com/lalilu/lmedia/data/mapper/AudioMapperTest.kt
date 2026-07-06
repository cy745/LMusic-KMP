package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioMapperTest {

    @Test
    fun `toDomain maps all fields correctly`() {
        val entity = LAudioEntity(
            id = "audio_test_1",
            title = "Test Song",
            subtitle = "Test Artist",
            mediaSourceName = "local",
            metadata = Metadata(title = "Meta Title", artist = "Meta Artist"),
            extra = mapOf("key" to "value"),
            available = true
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.subtitle, domain.subtitle)
        assertEquals(entity.mediaSourceName, domain.mediaSourceName)
        assertEquals(entity.metadata, domain.metadata)
        assertEquals(entity.extra, domain.extra)
        assertEquals(entity.available, domain.available)
    }

    @Test
    fun `toEntity maps all fields correctly`() {
        val domain = LAudio(
            id = "audio_test_2",
            title = "Another Song",
            subtitle = "Another Artist",
            mediaSourceName = "remote",
            metadata = Metadata(title = "Meta", duration = 200000L),
            extra = null,
            available = false
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.subtitle, entity.subtitle)
        assertEquals(domain.mediaSourceName, entity.mediaSourceName)
        assertEquals(domain.metadata, entity.metadata)
        assertEquals(domain.extra, entity.extra)
        assertEquals(domain.available, entity.available)
    }

    @Test
    fun `roundtrip preserves all fields`() {
        val original = LAudioEntity(
            id = "audio_roundtrip",
            title = "Round Trip",
            subtitle = "Test",
            mediaSourceName = "local",
            metadata = Metadata(title = "RT", album = "Test Album"),
            extra = mapOf("track" to "1"),
            available = true
        )

        val roundtripped = original.toDomain().toEntity()

        assertEquals(original, roundtripped)
    }

    @Test
    fun `empty metadata is preserved`() {
        val entity = LAudioEntity(
            id = "audio_empty_meta",
            title = "No Meta",
            subtitle = "",
            mediaSourceName = "test"
        )

        val domain = entity.toDomain()
        assertNotNull(domain.metadata)
        assertEquals(Metadata.EMPTY, domain.metadata)
    }

    @Test
    fun `null extra maps correctly`() {
        val domain = LAudio(
            id = "audio_no_extras",
            title = "Title",
            subtitle = "Sub",
            mediaSourceName = "test",
            extra = null
        )

        val entity = domain.toEntity()
        assertTrue(entity.extra == null)
    }
}
