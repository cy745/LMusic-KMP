package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.model.mediaKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayableQueueTest {
    private val a = LAudio(id = "a", mediaSourceName = "local")
    private val b = LAudio(id = "b", mediaSourceName = "local")
    private val c = LAudio(id = "c", mediaSourceName = "sandbox")
    private val d = LAudio(id = "d", mediaSourceName = "sandbox")

    @Test fun removingEarlierSongKeepsCurrentSong() = runTest {
        val queue = queue(listOf(a, b, c, d), 2)
        queue.update { remove(a) }
        assertEquals(c, queue.currentItem())
        assertEquals(1, queue.stateSnapshot().index)
    }

    @Test fun removingCurrentSelectsNextThenPreviousThenEmpty() = runTest {
        val queue = queue(listOf(a, b, c), 1)
        queue.update { remove(b) }
        assertEquals(c, queue.currentItem())
        queue.update { remove(c) }
        assertEquals(a, queue.currentItem())
        queue.update { remove(a) }
        assertNull(queue.currentItem())
        assertEquals(0, queue.stateSnapshot().index)
    }

    @Test fun sourceRemovalPreservesOtherSourceWithSameId() = runTest {
        val remote = a.copy(mediaSourceName = "remote")
        val queue = queue(listOf(a, remote, b, c), 1)
        queue.update { removeSource("local") }
        assertEquals(listOf(remote, c), queue.stateSnapshot().list)
        assertEquals(remote, queue.currentItem())
        queue.update { removeAll(setOf(a.mediaKey)) }
        assertEquals(remote, queue.currentItem())
    }

    @Test fun removeOneOccurrenceAndRemoveSongHaveDifferentSemantics() = runTest {
        val queue = queue(listOf(a, a, b), 1)
        queue.update { removeAt(0) }
        assertEquals(listOf(a, b), queue.stateSnapshot().list)
        assertEquals(0, queue.stateSnapshot().index)
        queue.update { addToEnd(listOf(a)); remove(a) }
        assertEquals(listOf(b), queue.stateSnapshot().list)
    }

    @Test fun insertingIntoEmptyQueueSelectsFirstItem() = runTest {
        val queue = PlayableQueueImpl()
        queue.update { addToStart(listOf(a, b)) }
        assertEquals(a, queue.currentItem())
        queue.update { addToStart(listOf(c, d)) }
        assertEquals(a, queue.currentItem())
        assertEquals(2, queue.stateSnapshot().index)
    }

    @Test fun movePreservesCurrentOccurrenceAcrossBothDirections() = runTest {
        val queue = queue(listOf(a, b, c, d), 2)
        queue.update { move(0, 3) }
        assertEquals(c, queue.currentItem())
        assertEquals(1, queue.stateSnapshot().index)
        queue.update { move(3, 0) }
        assertEquals(2, queue.stateSnapshot().index)
        queue.update { move(2, 0) }
        assertEquals(c, queue.currentItem())
        assertEquals(0, queue.stateSnapshot().index)
    }

    @Test fun metadataReplacementPreservesDuplicateOccurrenceAndSource() = runTest {
        val remote = a.copy(mediaSourceName = "remote")
        val queue = queue(listOf(remote, a, a, b), 2)
        queue.update { replaceAll(listOf(remote, a.copy(title = "new"), a.copy(title = "new"), b)) }
        assertEquals(2, queue.stateSnapshot().index)
        assertEquals(a.mediaKey, queue.currentItem()?.mediaKey)
    }

    @Test fun invalidIndicesAndClearRemainNormalized() = runTest {
        val queue = queue(listOf(a, b), -100)
        assertEquals(0, queue.stateSnapshot().index)
        queue.update { removeAt(-1); move(99, 0); replace(99, c); switchTo(99) }
        assertEquals(listOf(a, b), queue.stateSnapshot().list)
        queue.update { replaceAll(emptyList(), -1) }
        assertEquals(0, queue.stateSnapshot().index)
        queue.update { addToNext(listOf(c)) }
        assertEquals(c, queue.currentItem())
    }

    @Test fun atomicUpdatesDoNotLoseConcurrentInsertions() = runTest {
        val queue = queue(listOf(a), 0)
        (1..100).map { id -> async { queue.update { addToEnd(listOf(a.copy(id = "$id"))) } } }.awaitAll()
        assertEquals(101, queue.stateSnapshot().list.size)
        assertEquals(a, queue.currentItem())
    }

    @Test fun staleConditionalUpdateDoesNotOverwriteNewQueue() = runTest {
        val queue = queue(listOf(a, b), 0)
        val old = queue.stateSnapshot()
        queue.update { addToEnd(listOf(c)) }
        queue.update(predicate = { it == old }) { clear() }
        assertEquals(listOf(a, b, c), queue.stateSnapshot().list)
    }

    @Test fun rearrangedDisplayDoesNotMergeSourcesWithSameId() = runTest {
        val remote = a.copy(mediaSourceName = "remote")
        val queue = queue(listOf(a, remote), 1)
        assertEquals(listOf(remote, a), queue.stateSnapshot().rearrange())
        assertNull(queue.nextOf(c))
    }

    private suspend fun queue(items: List<LAudio>, index: Int) = PlayableQueueImpl().apply {
        update { replaceAll(items, index) }
    }
}
