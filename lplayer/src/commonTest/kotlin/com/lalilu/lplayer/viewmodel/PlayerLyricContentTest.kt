package com.lalilu.lplayer.viewmodel

import app.cash.turbine.test
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricContent
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.source.MediaContentState
import com.lalilu.lmedia.domain.source.MediaContentAvailability
import com.lalilu.lmedia.domain.source.MediaSource
import com.lalilu.lmedia.domain.source.Snapshot
import com.lalilu.lmedia.domain.source.SnapshotState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayerLyricContentTest {
    @Test
    fun unavailableSourcePublishesTerminalDocumentForCurrentAudio() = runTest {
        val source = FakeSource()
        val audio = LAudio(
            id = "audio-id",
            title = "Title",
            subtitle = "Artist",
            mediaSourceName = source.name,
        )

        observeLyricContent(audio, source) { error("lyrics must not be requested") }.test {
            source.contentState.value = MediaContentState(
                availability = MediaContentAvailability.Unavailable("offline"),
                generation = 3L,
            )

            val content = assertIs<LyricContent.Ready>(awaitItem())
            assertEquals(audio.id, content.key)
            assertEquals(3L, content.generation)
            assertEquals(emptyList<LyricItem>(), content.items)
            assertEquals("数据源不可用，无法加载歌词", content.emptyMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeSource : MediaSource {
        override val name: String = "source"
        override val state = MutableStateFlow<SnapshotState>(SnapshotState.Idle)
        override val snapshot = MutableStateFlow<Snapshot?>(null)
        override val contentState = MutableStateFlow(MediaContentState())
    }
}
