package com.lalilu.lhistory

import com.lalilu.lhistory.entity.*
import com.lalilu.lplayer.playback.ListeningObservation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.*

class ListeningRecorderTest {
    private class Fixture {
        val rows = mutableMapOf<Long, LHistory>()
        val buckets = mutableMapOf<Pair<Long, Long>, HistoryDay>()
        val recorder = ListeningRecorder({ row, days ->
            val id = row.id.takeIf { it != 0L } ?: (rows.size + 1).toLong()
            rows[id] = row.copy(id = id)
            days.forEach { buckets[id to it.dayStart] = it.copy(historyId = id) }
            id
        }, { TimeZone.UTC })
        suspend fun at(ms: Long, playing: Boolean = true, epoch: Long = 1, id: String = "1:sA",
                       duration: Long = 60000, seek: Boolean = false, complete: Boolean = false, wall: Long = ms) {
            recorder.accept(ListeningObservation(id, id, epoch, playing, duration, 0, seek, complete), wall, ms)
        }
    }
    @Test fun failedWritesDoNotLoseOldSessionWhenSelectionChanges() = runTest {
        var fail = true
        val saved = mutableListOf<LHistory>()
        val recorder = ListeningRecorder({ row, _ ->
            if (fail) error("disk temporarily unavailable")
            saved += row
            row.id.takeIf { it != 0L } ?: saved.size.toLong()
        }, { TimeZone.UTC })
        val a = ListeningObservation("1:sA", "A", 1, true, 60000, 0)
        recorder.accept(a, 0, 0)
        recorder.accept(a.copy(mediaId = "1:sB", epoch = 2, playing = false), 10000, 10000)
        fail = false
        recorder.accept(a.copy(mediaId = "1:sB", epoch = 2, playing = false), 15000, 15000)
        assertEquals(10000L, saved.single().duration)
        assertEquals("1:sA", saved.single().contentId)
    }

    @Test fun clearDoesNotResurrectActiveOrPendingRecordsAndResumeStartsFresh() = runTest {
        val f = Fixture(); f.at(0); f.at(15000)
        f.rows.clear(); f.buckets.clear(); f.recorder.cleared()
        f.at(20000); f.at(40000)
        assertTrue(f.rows.isEmpty())
        f.at(45000, false); f.at(50000); f.at(55000, false)
        assertEquals(5000L, f.rows.values.single().duration)
        assertEquals(50000L, f.rows.values.single().startTime)
        assertEquals(1, f.rows.values.single().playCount)
    }

    @Test fun explicitSelectionUpdatesRecencyBeforeAudioStartsButSilentRestoreDoesNot() = runTest {
        val rows = mutableMapOf<Long, LHistory>()
        val recorder = ListeningRecorder({ row, _ ->
            val id = row.id.takeIf { it != 0L } ?: (rows.size + 1).toLong()
            rows[id] = row.copy(id = id); id
        }, { TimeZone.UTC })
        val selected = ListeningObservation("1:sA", "A", 1, false, 60000, 0)
        recorder.accept(selected, 0, 0)
        assertTrue(rows.isEmpty())
        recorder.accept(selected.copy(requested = true), 10, 10)
        assertEquals(10L, rows.values.single().startTime)
        assertEquals(0, rows.values.single().playCount)
        recorder.accept(selected.copy(requested = true, playing = true), 1000, 1000)
        assertEquals(1, rows.values.single().playCount)
        assertEquals(0L, rows.values.single().duration)
    }

    @Test fun selectionIsNotPlaybackAndFirstStartIsImmediatelyVisible() = runTest {
        val f = Fixture(); f.at(0, false); assertTrue(f.rows.isEmpty())
        f.at(100, true); assertEquals(0L, f.rows.values.single().duration)
        assertEquals(100L, f.rows.values.single().startTime)
    }
    @Test fun pauseBufferAndSeekDoNotAddDurationOrRepeatedCounts() = runTest {
        val f = Fixture(); f.at(0); f.at(10000, false); f.at(20000, false, seek = true)
        f.at(30000); f.at(50000); f.at(60000); f.at(60000, false)
        assertEquals(40000L, f.rows.values.single().duration)
        assertEquals(1, f.rows.values.single().playCount)
    }
    @Test fun gaplessSameSongRepeatStartsANewSession() = runTest {
        val f = Fixture(); f.at(0, duration = 10000); f.at(10000, duration = 10000, complete = true)
        f.at(10000, epoch = 2); f.at(15000, epoch = 2, playing = false)
        assertEquals(listOf(10000L, 5000L), f.rows.values.map { it.duration })
        assertEquals(listOf(1, 1), f.rows.values.map { it.playCount })
    }
    @Test fun seekingDoesNotCountTheSameStartedSongAgain() = runTest {
        val f = Fixture(); f.at(0, duration = 10000); f.at(1000, false, duration = 10000, seek = true)
        f.at(1000, duration = 10000); f.at(2000, duration = 10000, complete = true)
        assertEquals(1, f.rows.values.single().playCount)
    }
    @Test fun midnightSplitsTimeAndCountsOnceOnStartDay() = runTest {
        val f = Fixture(); f.at(0, wall = 86400000 - 10000); f.at(40000, false, wall = 86400000 + 30000)
        assertEquals(listOf(10000L, 30000L), f.buckets.values.map { it.duration })
        assertEquals(listOf(1, 0), f.buckets.values.map { it.playCount })
    }
    @Test fun rapidSelectionAndMetadataUpdatesDoNotCreateExtraRows() = runTest {
        val f = Fixture(); f.at(0); f.at(1000, false, epoch = 2, id = "1:sB")
        f.at(1001, false, epoch = 3, id = "1:sC"); f.at(1002, epoch = 3, id = "1:sC")
        f.at(1003, epoch = 3, id = "1:sC")
        assertEquals(listOf("1:sA", "1:sC"), f.rows.values.map { it.contentId })
    }
}
