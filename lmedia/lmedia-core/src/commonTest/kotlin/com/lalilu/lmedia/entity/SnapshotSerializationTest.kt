/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.kotlin.fibonacci.com.lalilu.lmedia.entity

import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.Metadata
import com.lalilu.lmedia.entity.Snapshot
import com.lalilu.lmedia.entity.buildSnapshot
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
        ).buildSnapshot()

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<Snapshot>(serialized)

        println(serialized)
        println(deserialized)
    }
}