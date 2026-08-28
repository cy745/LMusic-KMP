package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MediaContentStateStoreTest {
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

    private class TestMediaSource(
        store: MediaSourceStateStore,
    ) : MediaSource {
        override val name: String = "test"
        override val state = store.state
        override val snapshot = store.snapshot
        override val contentState = store.contentState
    }
}
