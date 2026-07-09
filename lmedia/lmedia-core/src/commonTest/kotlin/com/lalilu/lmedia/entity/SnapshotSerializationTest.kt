package com.lalilu.lmedia.entity

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.Metadata
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.buildSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test

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
        val snapshot = listOf(
            LAudio(
                id = "1",
                title = "Audio 1",
                subtitle = "Subtitle 1",
                metadata = Metadata(artist = "周杰伦/夜的第七章"),
                extra = mapOf("key" to "value")
            ),
            LAudio(
                id = "2",
                title = "Audio 2",
                subtitle = "Subtitle 2",
                metadata = Metadata(artist = "张杰/周杰伦/告白气球")
            )
        ).let { buildSnapshot(it) }

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<Snapshot>(serialized)

        println(serialized)
        println(deserialized)
    }
}
