package com.lalilu.lplayer.playback

import com.lalilu.lmedia.domain.model.LAudio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformQueueBridgeTest {
    private val a = LAudio(id = "a")
    private val b = LAudio(id = "b")

    @Test fun insertAndSeekCannotBeSeparatedByAPlatformCallback() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val bridge = PlatformQueueBridge(applyToPlatform = {}, readPlatformAfterApply = {})
        var selected: LAudio? = null
        val command = async {
            bridge.editAndRun({ selectOrInsert(b) }) { snapshot ->
                entered.complete(Unit)
                release.await()
                selected = snapshot.currentItem()
            }
        }
        entered.await()
        val ticket = bridge.newPlatformSnapshot()
        val callback = async { bridge.acceptPlatformSnapshot(ticket, listOf(a), 0) }
        runCurrent()
        assertFalse(callback.isCompleted)
        release.complete(Unit)
        command.await()
        callback.await()
        assertEquals(b, selected)
        assertEquals(b, bridge.queue.currentItem())
    }

    @Test fun delayedPlatformLookupCannotUndoAppEdit() = runTest {
        val bridge = PlatformQueueBridge(applyToPlatform = {}, readPlatformAfterApply = {})
        val old = bridge.newPlatformSnapshot()
        bridge.queue.update { replaceAll(listOf(b), 0) }
        bridge.acceptPlatformSnapshot(old, listOf(a), 0)
        assertEquals(listOf(b), bridge.queue.stateSnapshot().list)
    }

    @Test fun onlyLatestPlatformSnapshotWins() = runTest {
        val bridge = PlatformQueueBridge(applyToPlatform = {}, readPlatformAfterApply = {})
        val old = bridge.newPlatformSnapshot()
        val latest = bridge.newPlatformSnapshot()
        bridge.acceptPlatformSnapshot(latest, listOf(b, a), 1)
        bridge.acceptPlatformSnapshot(old, listOf(a), 0)
        assertEquals(listOf(b, a), bridge.queue.stateSnapshot().list)
        assertEquals(1, bridge.queue.stateSnapshot().index)
    }

    @Test fun editWaitsForPlatformAndRejectsIntermediateCallback() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        lateinit var bridge: PlatformQueueBridge
        var intermediate = 0L
        bridge = PlatformQueueBridge(applyToPlatform = {
            intermediate = bridge.newPlatformSnapshot()
            entered.complete(Unit)
            finish.await()
        }, readPlatformAfterApply = {})
        val edit = async { bridge.queue.update { replaceAll(listOf(b), 0) } }
        entered.await()
        val callback = async { bridge.acceptPlatformSnapshot(intermediate, listOf(a), 0) }
        runCurrent()
        assertFalse(edit.isCompleted)
        assertFalse(callback.isCompleted)
        finish.complete(Unit)
        edit.await()
        callback.await()
        assertEquals(listOf(b), bridge.queue.stateSnapshot().list)
    }

    @Test fun metadataRefreshDoesNotWriteBackToPlatform() = runTest {
        var writes = 0
        val bridge = PlatformQueueBridge(applyToPlatform = { writes++ }, readPlatformAfterApply = {})
        bridge.queue.update { replaceAll(listOf(a), 0) }
        bridge.queue.update(QueueUpdateReason.Sync) { replaceAll(listOf(a.copy(title = "new")), -1) }
        assertEquals(1, writes)
    }

    @Test fun patchesHandleMultipleEditsAndDuplicates() {
        val lists = listOf(emptyList(), listOf("a"), listOf("a", "b", "a"),
            listOf("a", "b", "c", "d", "e"), listOf("x", "b", "y", "d", "z", "a"))
        for (old in lists) for (new in lists) {
            val actual = old.toMutableList()
            queueReplacements(old, new).forEach { patch ->
                actual.subList(patch.from, patch.to).clear()
                actual.addAll(patch.from, patch.items)
            }
            assertEquals(new, actual, "$old -> $new")
        }
    }

    @Test fun timelineResolutionPreservesOrderDuplicatesAndKnownMissingRows() {
        assertEquals(listOf(b, a, b), resolveTimelineQueue(listOf("b", "a", "b"), listOf(a, b), emptyList()))
        assertEquals(listOf(b, a), resolveTimelineQueue(listOf("b", "a"), listOf(a), listOf(b)))
        assertNull(resolveTimelineQueue(listOf("missing", "a"), listOf(a), emptyList()))
    }
}
