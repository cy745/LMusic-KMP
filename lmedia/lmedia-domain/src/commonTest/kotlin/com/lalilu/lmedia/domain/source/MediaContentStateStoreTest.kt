package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MediaContentStateStoreTest {
    @Test fun itemChangesOnlyInvalidateThatSongAndGlobalRefreshStillInvalidatesAll() {
        val store = MediaContentStateStore()
        store.ready()
        val a = store.state.value.forItem("a")
        val b = store.state.value.forItem("b")
        store.contentChanged("b")
        assertEquals(a, store.state.value.forItem("a"))
        assertEquals(b.generation + 1, store.state.value.forItem("b").generation)
        assertEquals(1L, store.state.value.generation)
        store.ready()
        assertEquals(a.generation + 1, store.state.value.forItem("a").generation)
        assertEquals(b.generation + 2, store.state.value.forItem("b").generation)
    }

    @Test
    fun refreshCanPreserveExistingReadyState() {
        val store = MediaContentStateStore()
        store.preparing(preserveReady = false)
        store.ready()

        store.preparing()
        store.unavailable("scan failed", preserveReady = true)

        assertIs<MediaContentAvailability.Ready>(store.state.value.availability)
        assertEquals(1L, store.state.value.generation)
    }

    @Test
    fun generationOnlyAdvancesWhenContentMayHaveChanged() {
        val store = MediaContentStateStore()

        store.ready()
        store.ready(contentChanged = false)
        store.ready()

        assertEquals(2L, store.state.value.generation)
    }

    @Test
    fun waitingConsumerContinuesUntilRetrySucceeds() = runTest {
        val syncStore = MediaSourceStateStore()
        val source = TestMediaSource(syncStore)
        val waiting = async { source.awaitContentReady() }
        runCurrent()

        syncStore.content.preparing(preserveReady = false)
        syncStore.content.unavailable("offline")
        runCurrent()
        assertFalse(waiting.isCompleted)

        syncStore.content.preparing(preserveReady = false)
        syncStore.content.ready()

        assertEquals(1L, waiting.await().generation)
    }

    @Test
    fun terminalWaitFailsWhenSourceBecomesUnavailable() = runTest {
        val syncStore = MediaSourceStateStore()
        val source = TestMediaSource(syncStore)
        syncStore.content.preparing(preserveReady = false)
        supervisorScope {
            val waiting = async { source.awaitContentReadyOrThrow() }
            runCurrent()

            syncStore.content.unavailable("disabled")

            assertFailsWith<MediaContentUnavailableException> { waiting.await() }
        }
    }

    private class TestMediaSource(
        store: MediaSourceStateStore,
    ) : MediaSource {
        override val name: String = "test"
        override val state = store.state
        override val snapshot = store.snapshot
        override val contentState = store.contentState
    }
}
