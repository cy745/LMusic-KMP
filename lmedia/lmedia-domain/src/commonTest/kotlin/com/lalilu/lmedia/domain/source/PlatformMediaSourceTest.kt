package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlatformMediaSourceTest {
    @Test
    fun rejectsDuplicatedSourceNames() {
        assertFailsWith<IllegalArgumentException> {
            PlatformMediaSource(
                listOf(
                    FakeMediaSource("duplicated"),
                    FakeMediaSource("duplicated"),
                )
            )
        }
    }

    private class FakeMediaSource(
        override val name: String,
    ) : MediaSource {
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
    }
}
