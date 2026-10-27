package com.lalilu.lmusic.repository

import com.lalilu.lhistory.entity.*
import com.lalilu.lhistory.repository.*
import com.lalilu.lmusic.impl.LMusicDatabase
import com.lalilu.lmusic.impl.requireDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class HistoryRefreshTest {
    @Test fun ownWritesRefreshRecencyStatisticsAndPagingEvenWithoutRoomInvalidations() = runBlocking {
        val db = requireDatabase<LMusicDatabase>(forceMemory = true)
        val real = db.historyDao()
        fun <T> frozen(read: suspend () -> T): Flow<T> = flow { emit(read()); awaitCancellation() }
        val noNotifications = object : LHistoryDao by real {
            override fun getFlowIdsMapWithLastTime(limit: Int) = frozen { real.getFlowIdsMapWithLastTime(limit).first() }
            override fun getFlowIdsMapWithCount(limit: Int) = frozen { real.getFlowIdsMapWithCount(limit).first() }
            override fun statistics(from: Long, until: Long) = frozen { real.statistics(from, until).first() }
            override fun dailyListening(from: Long, until: Long) = frozen { real.dailyListening(from, until).first() }
        }
        val repo = HistoryRepositoryImpl(object : ILHistoryDatabase { override fun historyDao() = noNotifications })
        val values = mutableListOf<ListeningStats>()
        val recents = mutableListOf<Map<String, Long>>()
        val recentCollection = launch { repo.getHistoriesIdsMapWithLastTime().collect { recents += it } }
        val collection = launch { repo.statistics(0, 10000).collect { values += it } }
        try {
            withTimeout(5000) { while (values.isEmpty() || recents.isEmpty()) delay(10) }
            val page = repo.getRange(0, 10000)
            repo.saveSession(LHistory(contentId = "1:sA", contentTitle = "A", startTime = 1, duration = 1, playCount = 1),
                listOf(HistoryDay(0, 0, 1, 1)))
            assertTrue(page.invalid)
            withTimeout(5000) { while (recents.last().size != 1) delay(10) }
            withTimeout(5000) { while (values.last().playCount != 1) delay(10) }
            repo.clearHistories()
            withTimeout(5000) { while (recents.last().isNotEmpty()) delay(10) }
            withTimeout(5000) { while (values.last().playCount != 0) delay(10) }
            assertEquals(ListeningStats(), values.last())
            assertEquals("rows=0, days=0, revision=2", repo.inspect())
        } finally { recentCollection.cancelAndJoin(); collection.cancelAndJoin(); repo.cancel(); db.close() }
    }
}
