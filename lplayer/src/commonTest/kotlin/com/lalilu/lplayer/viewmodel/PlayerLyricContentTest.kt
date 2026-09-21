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
            assertEquals(0L, content.generation, "不可用是稳定状态，版本固定为 0")
            assertEquals(emptyList<LyricItem>(), content.items)
            assertEquals("数据源不可用，无法加载歌词", content.emptyMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * 来源的"内容代次"会随任何快照/补丁更新自增（提取写回、进度上报都算），播放时它一直在变。
     * 歌词页把 `key + generation` 当作页面身份，所以歌词文档的版本必须只跟着歌词内容走：
     * 否则页面会被反复重建、`preparedForFollowing` 一次次归零——真机上表现为"歌词不跟随进度滚动"。
     */
    @Test
    fun sameLyricsKeepTheSameDocumentVersionWhileSourceGenerationChurns() = runTest {
        val source = FakeSource()
        val audio = LAudio(id = "audio-id", mediaSourceName = source.name)
        val items = listOf(LyricItem.NormalLyric(content = "line", time = 0, key = "0"))

        observeLyricContent(audio, source) { items }.test {
            source.contentState.value = MediaContentState(
                availability = MediaContentAvailability.Ready,
                generation = 1L,
            )
            val first = assertIs<LyricContent.Ready>(awaitItem())
            assertEquals(items.hashCode().toLong(), first.generation, "版本应当由歌词内容决定")

            source.contentState.value = MediaContentState(
                availability = MediaContentAvailability.Ready,
                generation = 42L,
            )
            val second = assertIs<LyricContent.Ready>(awaitItem())
            assertEquals(
                first.generation,
                second.generation,
                "来源代次变了但歌词没变：文档版本必须保持一致，页面才不会被重建",
            )
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
