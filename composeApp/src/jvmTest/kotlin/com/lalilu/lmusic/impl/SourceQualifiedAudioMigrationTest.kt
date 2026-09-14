package com.lalilu.lmusic.impl

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.executeSQL
import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceQualifiedAudioMigrationTest {
    @Test
    fun migrationPreservesRowsAndRelationsWithUnicodeAndOverlappingIds() = runTest {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.executeSQL("CREATE TABLE l_audio (song_id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, subtitle TEXT NOT NULL, media_source_name TEXT NOT NULL, extra TEXT, available INTEGER NOT NULL)")
            val first = MediaKey("💿本地", "42")
            val keys = listOf(first, MediaKey("remote", first.stableKey))
            for (key in keys) {
                val insert = connection.prepare("INSERT INTO l_audio VALUES (?, 'title', '', ?, NULL, 1)")
                try {
                    insert.bindText(1, key.id)
                    insert.bindText(2, key.sourceName)
                    insert.step()
                } finally {
                    insert.close()
                }
            }
            val relations = listOf("artist", "album", "genre")
            for (kind in relations) {
                connection.executeSQL("CREATE TABLE cross_ref_audio_x_$kind (${kind}_id TEXT NOT NULL, song_id TEXT NOT NULL, PRIMARY KEY (${kind}_id, song_id))")
                connection.executeSQL("INSERT INTO cross_ref_audio_x_$kind SELECT 'shared', song_id FROM l_audio")
            }

            MIGRATION_3_4.migrate(connection)

            val migrated = connection.prepare("SELECT raw_id, song_id FROM l_audio")
            try {
                val actual = buildMap {
                    while (migrated.step()) put(migrated.getText(0), migrated.getText(1))
                }
                assertEquals(keys.associate { it.id to it.stableKey }, actual)
            } finally {
                migrated.close()
            }
            for (kind in relations) {
                val query = connection.prepare("SELECT song_id FROM cross_ref_audio_x_$kind")
                try {
                    val actual = buildSet { while (query.step()) add(query.getText(0)) }
                    assertEquals(keys.map { it.stableKey }.toSet(), actual)
                } finally {
                    query.close()
                }
            }
        } finally {
            connection.close()
        }
    }
}
