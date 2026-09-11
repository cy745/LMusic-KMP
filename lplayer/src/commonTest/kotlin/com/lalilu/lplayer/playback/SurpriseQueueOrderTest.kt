package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SurpriseQueueOrderTest {
    @Test fun newSongAppearsAboveOldCurrentWithoutChangingMembership() = runTest {
        val original = (0..19).map { LAudio(id = "$it", mediaSourceName = "local") }
        for (start in original.indices) {
            val queue = PlayableQueueImpl()
            queue.update { replaceAll(original, start) }
            val oldCurrent = queue.currentItem()
            queue.update { prepareSurpriseNext(Random(7)) }
            assertEquals(oldCurrent, queue.currentItem())
            val next = SurpriseQueueOrder.nextIndex(original.size, start)
            queue.update { switchTo(next) }
            val visible = queue.stateSnapshot().rearrange()
            assertNotEquals(oldCurrent, visible[0])
            assertEquals(oldCurrent, visible[1])
            assertEquals(original.toSet(), visible.toSet())
            // This is a permutation, not an ever-growing duplicate history list.
            assertEquals(original.size, visible.size)
        }
    }

    @Test fun backwardsNavigationReturnsToPreviousSong() = runTest {
        val items = (0..9).map { LAudio(id = "$it") }
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(items, 0); prepareSurpriseNext(Random(4)) }
        queue.update { switchTo(SurpriseQueueOrder.nextIndex(items.size, 0)) }
        val current = queue.stateSnapshot().index
        queue.update { prepareSurpriseNext(Random(5)) }
        queue.update { switchTo(SurpriseQueueOrder.previousIndex(items.size, current)) }
        assertEquals(items.first(), queue.currentItem())
    }

    @Test fun emptyAndSingleSongNeverChooseInvalidCandidateOrReplaceCurrent() {
        assertEquals(-1, SurpriseQueueOrder.nextIndex(0, 0))
        assertEquals(-1, SurpriseQueueOrder.previousIndex(0, 0))
        assertEquals(-1, SurpriseQueueOrder.candidateIndex(0, 0))
        assertEquals(-1, SurpriseQueueOrder.candidateIndex(1, 0))
        assertEquals(0, SurpriseQueueOrder.nextIndex(1, 0))
        assertEquals(0, SurpriseQueueOrder.previousIndex(1, 0))
        for (size in 2..30) for (index in 0 until size) {
            val candidate = SurpriseQueueOrder.candidateIndex(size, index, Random(2))
            assertTrue(candidate in 0 until size)
            assertNotEquals(index, candidate)
        }
    }

    @Test fun preparingNextKeepsRecentVisibleHistoryForLargeQueues() = runTest {
        val items = (0..39).map { LAudio(id = "$it") }
        val queue = PlayableQueueImpl()
        queue.update { replaceAll(items, 15) }
        repeat(100) { seed ->
            val before = queue.stateSnapshot().rearrange()
            queue.update { prepareSurpriseNext(Random(seed)) }
            assertEquals(before.take(5), queue.stateSnapshot().rearrange().take(5))
        }
    }
}
