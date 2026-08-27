package com.lalilu.lmedia.data.mapper

import com.lalilu.lmedia.data.entity.LAudioEntity
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `toEntity maps regular fields`() {
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
        assertEquals(domain.available, entity.available)
    }
}
