package com.lalilu.lmedia.domain.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun exposesOnlyPersistentlyEnabledSources() {
        val enablement = FakeEnablement(disabled = mutableSetOf("off"))
        val platform = PlatformMediaSource(
            sources = listOf(FakeMediaSource("on"), FakeMediaSource("off")),
            enablement = enablement,
        )

        assertEquals(listOf("on"), platform.enabledSources.map { it.name })
        platform.setEnabled("off", true)
        assertEquals(listOf("on", "off"), platform.enabledSources.map { it.name })
    }

    private class FakeEnablement(
        private val disabled: MutableSet<String>,
    ) : MediaSourceEnablement {
        override fun isEnabled(sourceName: String): Boolean = sourceName !in disabled
        override fun setEnabled(sourceName: String, enabled: Boolean) {
            if (enabled) disabled -= sourceName else disabled += sourceName
        }
    }

    private class FakeMediaSource(
        override val name: String,
    ) : MediaSource {
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
        override val contentState = MutableStateFlow(MediaContentState())
    }
}
