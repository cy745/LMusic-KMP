package com.lalilu.lmedia.entity

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.LAudioExtraKeys
import com.lalilu.lmedia.domain.source.Snapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotSerializationTest {

    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        allowStructuredMapKeys = true
    }

    @Test
    fun testSnapshot() {
        val snapshot = Snapshot(
            audios = listOf(LAudio(
                id = "1",
                title = "Audio 1",
                subtitle = "Subtitle 1",
                extra = mapOf(
                    LAudioExtraKeys.ArtistName to "周杰伦/夜的第七章",
                    "key" to "value",
                )
            ),
            LAudio(
                id = "2",
                title = "Audio 2",
                subtitle = "Subtitle 2",
                extra = mapOf(LAudioExtraKeys.ArtistName to "张杰/周杰伦/告白气球"),
            )),
            revision = 3L,
        )

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<Snapshot>(serialized)

        assertEquals(snapshot, deserialized)
    }
}
