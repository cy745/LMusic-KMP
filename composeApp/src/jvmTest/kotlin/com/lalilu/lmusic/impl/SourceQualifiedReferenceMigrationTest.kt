package com.lalilu.lmusic.impl

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.executeSQL
import com.lalilu.lmedia.data.database.converter.StringListConverter
import com.lalilu.lmedia.domain.model.MediaKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceQualifiedReferenceMigrationTest {
    @Test fun preservesHistoryAndPlaylistButDoesNotGuessAmbiguousOrMissingReferences() = runTest {
        val connection = BundledSQLiteDriver().open(":memory:")
        val unique = MediaKey("local", "unique")
        val converter = StringListConverter()
        try {
            connection.executeSQL("CREATE TABLE l_audio (raw_id TEXT NOT NULL, song_id TEXT NOT NULL PRIMARY KEY)")
            for (key in listOf(unique, MediaKey("local", "42"), MediaKey("remote", "42"))) {
                val insert = connection.prepare("INSERT INTO l_audio VALUES (?, ?)")
                try {
                    insert.bindText(1, key.id)
                    insert.bindText(2, key.stableKey)
                    insert.step()
                } finally { insert.close() }
            }
            connection.executeSQL("CREATE TABLE m_history (id INTEGER PRIMARY KEY, contentId TEXT NOT NULL, contentTitle TEXT NOT NULL)")
            val rawIds = listOf(unique.id, "42", unique.stableKey, "missing")
            rawIds.forEachIndexed { index, rawId ->
                val insert = connection.prepare("INSERT INTO m_history VALUES (?, ?, 'retained title')")
                try {
                    insert.bindLong(1, index.toLong())
                    insert.bindText(2, rawId)
                    insert.step()
                } finally { insert.close() }
            }
            connection.executeSQL("CREATE TABLE l_playlist (id TEXT PRIMARY KEY, mediaIds TEXT NOT NULL, title TEXT NOT NULL)")
            val insertPlaylist = connection.prepare("INSERT INTO l_playlist VALUES ('favorites', ?, 'retained playlist')")
            try {
                insertPlaylist.bindText(1, converter.fromStringList(rawIds + unique.id))
                insertPlaylist.step()
            } finally { insertPlaylist.close() }

            MIGRATION_4_5.migrate(connection)

            val expected = listOf(unique.stableKey, "unresolved:42", "unresolved:${unique.stableKey}", "unresolved:missing")
            val history = connection.prepare("SELECT contentId, contentTitle FROM m_history ORDER BY id")
            try {
                val actual = buildList {
                    while (history.step()) {
                        add(history.getText(0))
                        assertEquals("retained title", history.getText(1))
                    }
                }
                assertEquals(expected, actual)
            } finally { history.close() }
            val playlist = connection.prepare("SELECT mediaIds, title FROM l_playlist WHERE id = 'favorites'")
            try {
                assertTrue(playlist.step())
                assertEquals(expected + unique.stableKey, converter.toStringList(playlist.getText(0)))
                assertEquals("retained playlist", playlist.getText(1))
            } finally { playlist.close() }
        } finally { connection.close() }
    }
}
