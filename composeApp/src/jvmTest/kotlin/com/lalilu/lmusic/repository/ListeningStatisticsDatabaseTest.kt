package com.lalilu.lmusic.repository

import com.lalilu.lhistory.entity.*
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ListeningStatisticsDatabaseTest {
    @Test fun persistentQueriesSumSessionsDeduplicateSourcesAndClearBothTables() = runTest {
        val db = requireDatabase<LMusicDatabase>(forceMemory = true)
        try {
            val dao = db.historyDao()
            dao.saveSession(LHistory(contentId = "5:localA", contentTitle = "A", duration = 60000, playCount = 1),
                listOf(HistoryDay(0, 0, 10000, 0), HistoryDay(0, 86400000, 50000, 1)))
            dao.saveSession(LHistory(contentId = "5:localA", contentTitle = "A again", duration = 5000),
                listOf(HistoryDay(0, 86400000, 5000, 0)))
            dao.saveSession(LHistory(contentId = "6:remoteA", contentTitle = "A remote", duration = 30000, playCount = 1),
                listOf(HistoryDay(0, 86400000, 30000, 1)))
            assertEquals(ListeningStats(85000, 2, 2), dao.statistics(86400000, 172800000).first())
            assertEquals(listOf(DailyListening(0, 10000), DailyListening(86400000, 85000)), dao.dailyListening(0, 172800000).first())
            assertEquals(1, dao.getFlowIdsMapWithCount(10).first()["5:localA"])
            dao.clear()
            assertEquals(ListeningStats(), dao.statistics(0, Long.MAX_VALUE).first())
            assertTrue(dao.dailyListening(0, Long.MAX_VALUE).first().isEmpty())
            assertTrue(dao.getFlowIdsMapWithLastTime(10).first().isEmpty())
        } finally { db.close() }
    }
}
