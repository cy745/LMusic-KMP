package com.lalilu.lmusic.impl

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.executeSQL
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class ListeningHistoryMigrationTest {
    @Test fun preservesOldHistoryWithoutInventingDailyStatistics() = runTest {
        val db = BundledSQLiteDriver().open(":memory:")
        try {
            db.executeSQL("CREATE TABLE m_history (id INTEGER PRIMARY KEY, duration INTEGER NOT NULL)")
            db.executeSQL("INSERT INTO m_history VALUES (1, -1), (2, 5000), (3, 30000)")
            MIGRATION_5_6.migrate(db)
            val rows = db.prepare("SELECT playCount FROM m_history ORDER BY id")
            try {
                val counts = buildList { while (rows.step()) add(rows.getLong(0)) }
                assertEquals(listOf(0L, 0L, 1L), counts)
            } finally { rows.close() }
            val days = db.prepare("SELECT COUNT(*) FROM history_day")
            try { assertTrue(days.step()); assertEquals(0L, days.getLong(0)) } finally { days.close() }
        } finally { db.close() }
    }
}
